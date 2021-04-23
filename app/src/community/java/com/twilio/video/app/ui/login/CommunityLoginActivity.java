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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.view.WindowInsets;
//import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.twilio.video.app.R;
import com.twilio.video.app.auth.Authenticator;
import com.twilio.video.app.auth.CommunityLoginResult.CommunityLoginFailureResult;
import com.twilio.video.app.auth.CommunityLoginResult.CommunityLoginSuccessResult;
import com.twilio.video.app.auth.LoginEvent.CommunityLoginEvent;
import com.twilio.video.app.auth.LoginResult;
import com.twilio.video.app.base.BaseActivity;
import com.twilio.video.app.data.api.AuthServiceError;
import com.twilio.video.app.databinding.CommunityLoginActivityBinding;
import com.twilio.video.app.databinding.TabletLoginActivityBinding;
import com.twilio.video.app.ui.room.RoomActivity;
import com.twilio.video.app.util.InputUtils;

import io.orcana.DeviceInfo;
import io.orcana.OTWrapper;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import javax.inject.Inject;
import timber.log.Timber;

import com.vuzix.sdk.barcode.ScannerIntent;
import com.vuzix.sdk.barcode.ScanResult2;

// TODO Create view model and fragment for this screen
public class CommunityLoginActivity extends BaseActivity {
    private static final int REQUEST_CODE_SCAN = 0;

    private CommunityLoginActivityBinding binding;
    private TabletLoginActivityBinding tabletBinding;

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

        if(DeviceInfo.isHeadset()){
            binding = CommunityLoginActivityBinding.inflate(getLayoutInflater());

            binding.name.addTextChangedListener(textWatcher);
            binding.name.setText(R.string.default_user_name);

            binding.passcode.addTextChangedListener(textWatcher);
            binding.caseId.addTextChangedListener(textWatcher);

            binding.login.setOnClickListener(this::loginClicked);
            binding.scanButton.setOnClickListener(this::scanClicked);

            binding.manualEntryLink.setOnClickListener(this::typeInfoSwitchClicked);
            binding.manualEntryLink.setOnFocusChangeListener(this::onLinkHover);

            binding.LoginInfo.setVisibility(View.GONE);
            binding.ButtonLogin.setVisibility(View.VISIBLE);

            binding.scanButton.requestFocus();

            binding.versionHUD.setText(OTWrapper.version);

            setContentView(binding.getRoot());
        } else {
            if(!DeviceInfo.isPhone()){
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }

            tabletBinding = TabletLoginActivityBinding.inflate(getLayoutInflater());
            tabletBinding.name.addTextChangedListener(textWatcher);
            tabletBinding.passcode.addTextChangedListener(textWatcher);
            tabletBinding.caseId.addTextChangedListener(textWatcher);
            tabletBinding.inputContinue.setOnClickListener(this::loginClicked);

            tabletBinding.checkBox.setMovementMethod(LinkMovementMethod.getInstance());
            tabletBinding.checkBox.setOnCheckedChangeListener(this::onCheckedChanged);

//            tabletBinding.qrContinue.setOnClickListener(this::loginClicked);
//            tabletBinding.inputContinue.setOnClickListener(this::inputContinueClicked);

//            tabletBinding.QRCodeLayout.setVisibility(View.GONE);
//            tabletBinding.TypeInfoLayout.setVisibility(View.VISIBLE);

            tabletBinding.versionTab.setText(OTWrapper.version);

            setContentView(tabletBinding.getRoot());
        }

        if (authenticator.loggedIn()) startLobbyActivity();

        // Hide the status bar.
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            final WindowInsetsController insetsController = getWindow().getInsetsController();
//            if (insetsController != null) {
//                insetsController.hide(WindowInsets.Type.statusBars());
//            }
//        } else {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
//        }

//        final Handler handler = new Handler(Looper.getMainLooper());
//        handler.postDelayed(() -> loginClicked(null), 3000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                ScanResult2 scanResult = data.getParcelableExtra(ScannerIntent.RESULT_EXTRA_SCAN_RESULT2);
                binding.caseId.setText(scanResult.getText());
                loginClicked(null);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void passcodeChanged(Editable editable) {
        enableLoginButton(isInputValid());
    }

//    private void inputContinueClicked(View view){
//        tabletBinding.TypeInfoLayout.setVisibility(View.GONE);
//        tabletBinding.QRCodeLayout.setVisibility(View.VISIBLE);
//
//        try {
//            Bitmap bitmap = QRCodeGenerator.getQRCodeImage(tabletBinding.caseId.getText().toString(), 512, 512);
//            tabletBinding.QRCodeView.setImageBitmap(bitmap);
//        } catch (WriterException e) {
//            e.printStackTrace();
//        }
//    }

    private void scanClicked(View view){
        Intent scannerIntent = new Intent(ScannerIntent.ACTION);
        startActivityForResult(scannerIntent, REQUEST_CODE_SCAN);
    }

    private void typeInfoSwitchClicked(View view){
        binding.ButtonLogin.setVisibility(View.GONE);
        binding.LoginInfo.setVisibility(View.VISIBLE);

        binding.caseId.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(binding.caseId, InputMethodManager.SHOW_IMPLICIT);
    }

    private void onLinkHover(View view, boolean b){
        TextView tv = (TextView)view;
        if(b){
            Timber.d("Enter");
            SpannableString content = new SpannableString(getString(R.string.manual_entry_link_text));
            content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
            tv.setText(content);
        } else {
            Timber.d("Exit");
            tv.setText(getString(R.string.manual_entry_link_text));
        }
    }

    void onCheckedChanged(CompoundButton var1, boolean var2){
        enableLoginButton(isInputValid());
    }

    private void loginClicked(View view) {
        if(DeviceInfo.isHeadset()){
            String identity = binding.name.getText().toString() + DeviceInfo.brandWithDelimiter();
            String passcode = binding.passcode.getText().toString();
            String caseID = binding.caseId.getText().toString();
            login(identity, passcode, caseID);
            return;
        }

        String identity = tabletBinding.name.getText().toString() + DeviceInfo.brandWithDelimiter();
        String passcode = tabletBinding.passcode.getText().toString();
        String caseID = tabletBinding.caseId.getText().toString();
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
                    binding.passcodeInput2.setError(errorMessage);
                    binding.passcodeInput2.setErrorEnabled(true);
                    return;
                case EXPIRED_PASSCODE_ERROR:
                    errorMessage = getString(R.string.login_screen_expired_passcode_error);
                    binding.passcodeInput2.setError(errorMessage);
                    binding.passcodeInput2.setErrorEnabled(true);
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
        if(DeviceInfo.isHeadset()){
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.passcodeInput2.setErrorEnabled(false);
        }
    }

    private void postLoginViewState() {
        if(DeviceInfo.isHeadset()){
            binding.progressBar.setVisibility(View.GONE);
            enableLoginButton(true);
        }
    }

    private boolean isInputValid() {
        if(DeviceInfo.isHeadset()){
            Editable nameEditable = binding.name.getText();
            Editable passcodeEditable = binding.passcode.getText();
            Editable caseIDEditable = binding.caseId.getText();

            return nameEditable != null
                    && passcodeEditable != null
                    && caseIDEditable != null
                    && !nameEditable.toString().isEmpty()
                    && !passcodeEditable.toString().isEmpty()
                    && !caseIDEditable.toString().isEmpty();
        }

        Editable nameEditable = tabletBinding.name.getText();
        Editable passcodeEditable = tabletBinding.passcode.getText();
        Editable caseIDEditable = tabletBinding.caseId.getText();
        return nameEditable != null
                && passcodeEditable != null
                && caseIDEditable != null
                && !nameEditable.toString().isEmpty()
                && !passcodeEditable.toString().isEmpty()
                && !caseIDEditable.toString().isEmpty()
                && tabletBinding.checkBox.isChecked();
    }

    private void enableLoginButton(boolean isEnabled) {
        if(DeviceInfo.isHeadset()){
            if (isEnabled) {
    //            binding.login.setTextColor(ResourcesCompat.getColor(getResources(), R.color.orcanaBlack, null));
                binding.login.setEnabled(true);
            } else {
    //            binding.login.setTextColor(ResourcesCompat.getColor(getResources(), R.color.cream, null));
                binding.login.setEnabled(false);
            }
        } else {
            if (isEnabled) {
                //            binding.login.setTextColor(ResourcesCompat.getColor(getResources(), R.color.orcanaBlack, null));
                tabletBinding.inputContinue.setEnabled(true);
            } else {
                //            binding.login.setTextColor(ResourcesCompat.getColor(getResources(), R.color.cream, null));
                tabletBinding.inputContinue.setEnabled(false);
            }
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
