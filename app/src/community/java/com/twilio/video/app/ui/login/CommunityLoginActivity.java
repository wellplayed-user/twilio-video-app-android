/*
 * Copyright (C) 2019 Twilio, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twilio.video.app.ui.login;

import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.core.content.res.ResourcesCompat;
import com.twilio.video.app.R;
import com.twilio.video.app.auth.Authenticator;
import com.twilio.video.app.auth.CommunityLoginResult.CommunityLoginFailureResult;
import com.twilio.video.app.auth.CommunityLoginResult.CommunityLoginSuccessResult;
import com.twilio.video.app.auth.LoginEvent.CommunityLoginEvent;
import com.twilio.video.app.auth.LoginResult;
import com.twilio.video.app.base.BaseActivity;
import com.twilio.video.app.data.api.AuthServiceError;
import com.twilio.video.app.databinding.CommunityLoginActivityBinding;
import com.twilio.video.app.ui.room.RoomActivity;
import com.twilio.video.app.util.InputUtils;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import javax.inject.Inject;
import timber.log.Timber;

// TODO Create view model and fragment for this screen
public class CommunityLoginActivity extends BaseActivity {
    private CommunityLoginActivityBinding binding;

    @Inject Authenticator authenticator;
    TextWatcher textWatcher =
            new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    passcodeChanged(s);
                }
            };
    CompositeDisposable disposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = CommunityLoginActivityBinding.inflate(getLayoutInflater());
        binding.name.addTextChangedListener(textWatcher);
        binding.passcode.addTextChangedListener(textWatcher);
        binding.caseId.addTextChangedListener(textWatcher);
        binding.login.setOnClickListener(this::loginClicked);
        setContentView(binding.getRoot());

        Timber.d("Device Key: %s ", ("$" + Build.BRAND + "$"));

        if (authenticator.loggedIn()) startLobbyActivity();

        // Hide the status bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final WindowInsetsController insetsController = getWindow().getInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars());
            }
        } else {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
        }

//        final Handler handler = new Handler(Looper.getMainLooper());
//        handler.postDelayed(() -> loginClicked(null), 3000);
    }

    private void passcodeChanged(Editable editable) {
        enableLoginButton(isInputValid());
    }

    private void loginClicked(View view) {
        String identity = binding.name.getText().toString() + ("$" + Build.BRAND + "$");
        String passcode = binding.passcode.getText().toString();
        String caseID = binding.caseId.getText().toString();
        login(identity, passcode, caseID);
    }

    private void login(String identity, String passcode, String caseID) {
        preLoginViewState();

        disposable.add(
                authenticator
                        .login(new CommunityLoginEvent(identity, passcode, caseID))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(this::postLoginViewState)
                        .subscribe(
                                loginResult -> {
                                    if (loginResult instanceof CommunityLoginSuccessResult)
                                        startLobbyActivity();
                                    else {
                                        handleAuthError(loginResult);
                                    }
                                },
                                exception -> {
                                    handleAuthError(null);
                                    Timber.e(exception);
                                }));
    }

    private void handleAuthError(LoginResult loginResult) {

        if (loginResult instanceof CommunityLoginFailureResult) {
            String errorMessage;
            AuthServiceError error = ((CommunityLoginFailureResult) loginResult).getError();
            switch (error) {
                case INVALID_PASSCODE_ERROR:
                    errorMessage = getString(R.string.login_screen_invalid_passcode_error);
                    binding.passcodeInput.setError(errorMessage);
                    binding.passcodeInput.setErrorEnabled(true);
                    return;
                case EXPIRED_PASSCODE_ERROR:
                    errorMessage = getString(R.string.login_screen_expired_passcode_error);
                    binding.passcodeInput.setError(errorMessage);
                    binding.passcodeInput.setErrorEnabled(true);
                    return;
                case CASE_DOES_NOT_EXIST:
                    displayAuthErrorOrcana(R.string.login_screen_auth_error_desc_CASE_DOES_NOT_EXIST);
                    return;
                case CASE_IS_NOT_ACTIVE:
                    displayAuthErrorOrcana(R.string.login_screen_auth_error_desc_CASE_IS_NOT_ACTIVE);
                    return;
            }
        }

        displayAuthError();
    }

    private void preLoginViewState() {
        InputUtils.hideKeyboard(this);
        enableLoginButton(false);
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.passcodeInput.setErrorEnabled(false);
    }

    private void postLoginViewState() {
        binding.progressBar.setVisibility(View.GONE);
        enableLoginButton(true);
    }

    private boolean isInputValid() {
        Editable nameEditable = binding.name.getText();
        Editable passcodeEditable = binding.passcode.getText();
        Editable caseIDEditable = binding.caseId.getText();

        if (nameEditable != null
                && passcodeEditable != null
                && caseIDEditable != null
                && !nameEditable.toString().isEmpty()
                && !passcodeEditable.toString().isEmpty()
                && !caseIDEditable.toString().isEmpty()) {
            return true;
        }
        return false;
    }

    private void enableLoginButton(boolean isEnabled) {
        if (isEnabled) {
            binding.login.setTextColor(ResourcesCompat.getColor(getResources(), R.color.orcanaBlack, null));
            binding.login.setEnabled(true);
        } else {
            binding.login.setTextColor(ResourcesCompat.getColor(getResources(), R.color.cream, null));
            binding.login.setEnabled(false);
        }
    }

    private void startLobbyActivity() {
        RoomActivity.Companion.startActivity(this, getIntent().getData());
        finish();
    }

    private void displayAuthError() {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(getString(R.string.login_screen_error_title))
                .setMessage(getString(R.string.login_screen_auth_error_desc))
                .setPositiveButton("OK", null)
                .show();
    }

    private void displayAuthErrorOrcana(int resID) {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(getString(R.string.login_screen_error_title_orcana))
                .setMessage(getString(resID))
                .setPositiveButton("OK", null)
                .show();
    }
}
