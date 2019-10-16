package com.beemdevelopment.aegis.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;

import com.beemdevelopment.aegis.CancelAction;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.crypto.KeyStoreHandle;
import com.beemdevelopment.aegis.crypto.KeyStoreHandleException;
import com.beemdevelopment.aegis.db.DatabaseFileCredentials;
import com.beemdevelopment.aegis.db.slots.BiometricSlot;
import com.beemdevelopment.aegis.db.slots.PasswordSlot;
import com.beemdevelopment.aegis.db.slots.Slot;
import com.beemdevelopment.aegis.db.slots.SlotException;
import com.beemdevelopment.aegis.db.slots.SlotList;
import com.beemdevelopment.aegis.helpers.BiometricHelper;
import com.beemdevelopment.aegis.helpers.EditTextHelper;
import com.beemdevelopment.aegis.helpers.UiThreadExecutor;
import com.beemdevelopment.aegis.ui.tasks.SlotListTask;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class AuthActivity extends AegisActivity implements SlotListTask.Callback {
    private EditText _textPassword;

    private CancelAction _cancelAction;
    private SlotList _slots;
    private BiometricPrompt.CryptoObject _bioCryptoObj;
    private BiometricPrompt _bioPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        _textPassword = findViewById(R.id.text_password);
        LinearLayout boxBiometricInfo = findViewById(R.id.box_biometric_info);
        Button decryptButton = findViewById(R.id.button_decrypt);

        _textPassword.setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                decryptButton.performClick();
            }
            return false;
        });

        Intent intent = getIntent();
        _slots = (SlotList) intent.getSerializableExtra("slots");
        _cancelAction = (CancelAction) intent.getSerializableExtra("cancelAction");

        // only show the biometric prompt if the api version is new enough, permission is granted, a scanner is found and a biometric slot is found
        if (_slots.has(BiometricSlot.class) && BiometricHelper.isSupported() && BiometricHelper.isAvailable(this)) {
            boolean invalidated = false;

            try {
                // find a biometric slot with an id that matches an alias in the keystore
                for (BiometricSlot slot : _slots.findAll(BiometricSlot.class)) {
                    String id = slot.getUUID().toString();
                    KeyStoreHandle handle = new KeyStoreHandle();
                    if (handle.containsKey(id)) {
                        SecretKey key = handle.getKey(id);
                        // if 'key' is null, it was permanently invalidated
                        if (key == null) {
                            invalidated = true;
                            continue;
                        }

                        Cipher cipher = slot.createDecryptCipher(key);
                        _bioCryptoObj = new BiometricPrompt.CryptoObject(cipher);
                        _bioPrompt = new BiometricPrompt(this, new UiThreadExecutor(), new BiometricPromptListener());
                        invalidated = false;
                        break;
                    }
                }
            } catch (KeyStoreHandleException | SlotException e) {
                throw new RuntimeException(e);
            }

            // display a help message if a matching invalidated keystore entry was found
            if (invalidated) {
                boxBiometricInfo.setVisibility(View.VISIBLE);
            }
        }

        decryptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                char[] password = EditTextHelper.getEditTextChars(_textPassword);
                trySlots(PasswordSlot.class, password);
            }
        });

        if (_bioCryptoObj == null) {
            _textPassword.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void showError() {
        Dialogs.showSecureDialog(new AlertDialog.Builder(this)
                .setTitle(getString(R.string.unlock_vault_error))
                .setMessage(getString(R.string.unlock_vault_error_description))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> selectPassword())
                .create());
    }

    private <T extends Slot> void trySlots(Class<T> type, Object obj) {
        SlotListTask.Params params = new SlotListTask.Params(_slots, obj);
        new SlotListTask<>(type, this, this).execute(params);
    }

    private void selectPassword() {
        _textPassword.selectAll();

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    @Override
    public void onBackPressed() {
        switch (_cancelAction) {
            case KILL:
                finishAffinity();

            case CLOSE:
                Intent intent = new Intent();
                setResult(RESULT_CANCELED, intent);
                finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (_bioPrompt != null) {
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Test title")
                    .setDescription("Test description")
                    .setNegativeButtonText(getString(android.R.string.cancel))
                    .build();
            _bioPrompt.authenticate(info, _bioCryptoObj);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (_bioPrompt != null) {
            _bioPrompt.cancelAuthentication();
        }
    }

    @Override
    public void onTaskFinished(SlotListTask.Result result) {
        if (result != null) {
            // replace the old slot with the repaired one
            if (result.isSlotRepaired()) {
                _slots.replace(result.getSlot());
            }

            // send the master key back to the main activity
            Intent intent = new Intent();
            intent.putExtra("creds", new DatabaseFileCredentials(result.getKey(), _slots));
            intent.putExtra("repairedSlot", result.isSlotRepaired());
            setResult(RESULT_OK, intent);
            finish();
        } else {
            showError();
        }
    }

    private class BiometricPromptListener extends BiometricPrompt.AuthenticationCallback {
        @Override
        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
            super.onAuthenticationError(errorCode, errString);
        }

        @Override
        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
            super.onAuthenticationSucceeded(result);
            trySlots(BiometricSlot.class, _bioCryptoObj.getCipher());
        }

        @Override
        public void onAuthenticationFailed() {
            super.onAuthenticationFailed();
        }
    }
}
