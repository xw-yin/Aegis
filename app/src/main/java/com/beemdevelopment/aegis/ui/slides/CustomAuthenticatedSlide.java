package com.beemdevelopment.aegis.ui.slides;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;

import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.db.slots.BiometricSlot;
import com.beemdevelopment.aegis.helpers.BiometricSlotInitializer;
import com.beemdevelopment.aegis.helpers.EditTextHelper;
import com.github.paolorotolo.appintro.ISlidePolicy;
import com.github.paolorotolo.appintro.ISlideSelectionListener;
import com.google.android.material.snackbar.Snackbar;

import javax.crypto.Cipher;

public class CustomAuthenticatedSlide extends Fragment implements ISlidePolicy, ISlideSelectionListener {
    private int _cryptType;
    private EditText _textPassword;
    private EditText _textPasswordConfirm;
    private int _bgColor;

    private BiometricSlot _bioSlot;
    private Cipher _bioCipher;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_authenticated_slide, container, false);
        _textPassword = view.findViewById(R.id.text_password);
        _textPasswordConfirm = view.findViewById(R.id.text_password_confirm);
        view.findViewById(R.id.main).setBackgroundColor(_bgColor);
        return view;
    }

    public int getCryptType() {
        return _cryptType;
    }

    public BiometricSlot getBiometricSlot() {
        return _bioSlot;
    }

    public Cipher getBiometriCipher() {
        return _bioCipher;
    }

    public char[] getPassword() {
        return EditTextHelper.getEditTextChars(_textPassword);
    }

    public void setBgColor(int color) {
        _bgColor = color;
    }

    public void showBiometricPrompt() {
        BiometricSlotInitializer initializer = new BiometricSlotInitializer(this, new BiometricsListener());
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Test title")
                .setDescription("Test description")
                .setNegativeButtonText(getString(android.R.string.cancel))
                .build();
        initializer.authenticate(info);
    }

    @Override
    public void onSlideSelected() {
        Intent intent = getActivity().getIntent();
        _cryptType = intent.getIntExtra("cryptType", CustomAuthenticationSlide.CRYPT_TYPE_INVALID);

        if (_cryptType == CustomAuthenticationSlide.CRYPT_TYPE_BIOMETRIC) {
            showBiometricPrompt();
        }
    }

    @Override
    public void onSlideDeselected() {

    }

    @Override
    public boolean isPolicyRespected() {
        switch (_cryptType) {
            case CustomAuthenticationSlide.CRYPT_TYPE_NONE:
                return true;
            case CustomAuthenticationSlide.CRYPT_TYPE_BIOMETRIC:
                if (_bioSlot == null) {
                    return false;
                }
                // intentional fallthrough
            case CustomAuthenticationSlide.CRYPT_TYPE_PASS:
                return EditTextHelper.areEditTextsEqual(_textPassword, _textPasswordConfirm);
            default:
                return false;
        }
    }

    @Override
    public void onUserIllegallyRequestedNextPage() {
        String message;
        if (!EditTextHelper.areEditTextsEqual(_textPassword, _textPasswordConfirm)) {
            message = getString(R.string.password_equality_error);
        } else if (_bioSlot == null) {
            message = getString(R.string.register_fingerprint);
            showBiometricPrompt();
        } else {
            return;
        }

        View view = getView();
        if (view != null) {
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
            snackbar.show();
        }
    }

    private class BiometricsListener implements BiometricSlotInitializer.Listener {

        @Override
        public void onInitializeSlot(BiometricSlot slot, Cipher cipher) {
            _bioSlot = slot;
            _bioCipher = cipher;
        }

        @Override
        public void onSlotInitializationFailed(Exception e) {

        }
    }
}
