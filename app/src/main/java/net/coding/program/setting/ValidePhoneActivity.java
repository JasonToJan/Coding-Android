package net.coding.program.setting;

import android.app.Activity;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import com.loopj.android.http.RequestParams;

import net.coding.program.MyApp;
import net.coding.program.R;
import net.coding.program.common.Global;
import net.coding.program.common.SimpleSHA1;
import net.coding.program.common.WeakRefHander;
import net.coding.program.common.base.MyJsonResponse;
import net.coding.program.common.network.MyAsyncHttpClient;
import net.coding.program.common.ui.BackActivity;
import net.coding.program.common.util.ViewStyleUtil;
import net.coding.program.common.widget.LoginEditText;
import net.coding.program.common.widget.ValidePhoneView;
import net.coding.program.login.auth.AuthInfo;
import net.coding.program.login.auth.TotpClock;
import net.coding.program.login.phone.CountryPickActivity_;
import net.coding.program.model.AccountInfo;
import net.coding.program.model.PhoneCountry;
import net.coding.program.model.UserObject;
import net.coding.program.user.UserPointActivity_;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.ViewById;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by chenchao on 15/12/28.
 */

@EActivity(R.layout.activity_valide_phone)
public class ValidePhoneActivity extends BackActivity {

    private static final int RESULT_PICK_COUNTRY = 10;

    private static final String TAG_SET_USER_INFO = "TAG_SET_USER_INFO";

    @ViewById
    LoginEditText editPhone, editCode, passwordEdit, twoFAEdit;

    @ViewById
    TextView loginButton, countryCode;

    @ViewById
    ValidePhoneView sendPhoneMessage;

    UserObject user;

    Handler handler2FA;

    PhoneCountry pickCountry = PhoneCountry.getChina();

    boolean isFirstSet = false;

    @AfterViews
    final void initValidePhoneActivity() {
        // TODO: 2016/12/22 这个版本不上这个功能
//        isFirstSet = MyApp.sUserObject.phone.isEmpty();

        ViewStyleUtil.editTextBindButton(loginButton, editPhone, editCode);
        user = AccountInfo.loadAccount(this);
        sendPhoneMessage.setEditPhone(editPhone);
        sendPhoneMessage.setUrl(ValidePhoneView.CHANGE_PHONE);

        handler2FA = new WeakRefHander(msg -> {
            if (twoFAEdit.getVisibility() == View.VISIBLE) {
                String secret = AccountInfo.loadAuth(this, MyApp.sUserObject.global_key);
                if (secret.isEmpty()) {
                    return true;
                }

                String code2FA = new AuthInfo(secret, new TotpClock(this)).getCode();
                twoFAEdit.setText(code2FA);
            }

            return true;
        }, 100);

        final String url = Global.HOST_API + "/user/2fa/method";
        MyAsyncHttpClient.get(this, url, new MyJsonResponse(this) {
            @Override
            public void onMySuccess(JSONObject response) {
                String type = response.optString("data");
                if (type.equals("password")) {
                    passwordEdit.setVisibility(View.VISIBLE);
                    twoFAEdit.setVisibility(View.GONE);
                    ViewStyleUtil.editTextBindButton(loginButton, editPhone, editCode, passwordEdit);
                } else {
                    passwordEdit.setVisibility(View.GONE);
                    twoFAEdit.setVisibility(View.VISIBLE);
                    ViewStyleUtil.editTextBindButton(loginButton, editPhone, editCode, twoFAEdit);
                    handler2FA.sendEmptyMessage(0);
                }
            }
        });

        bindCountry();
    }

    @Click
    void countryCode() {
        CountryPickActivity_.intent(this)
                .startForResult(RESULT_PICK_COUNTRY);
    }

    void bindCountry() {
        countryCode.setText(pickCountry.getCountryCode());
        sendPhoneMessage.setPhoneCountry(pickCountry);
    }

    @OnActivityResult(RESULT_PICK_COUNTRY)
    void onResultPickCountry(int resultCode, @OnActivityResult.Extra PhoneCountry resultData) {
        if (resultCode == Activity.RESULT_OK && resultData != null) {
            pickCountry = resultData;
            bindCountry();
        }
    }

    @Override
    protected void onStop() {
        sendPhoneMessage.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (handler2FA != null) {
            handler2FA.removeMessages(0);
        }

        super.onDestroy();
    }

    @Click
    void loginButton() {
        final String url = Global.HOST_API + "/account/phone/change";
        String phone = editPhone.getTextString();
        String code = editCode.getTextString();
        String phoneCountryCode = pickCountry.getCountryCode();
        String country = pickCountry.iso_code;
        String two_factor_code;
        if (twoFAEdit.getVisibility() == View.VISIBLE) {
            two_factor_code = SimpleSHA1.sha1(twoFAEdit.getTextString());
        } else {
            two_factor_code = SimpleSHA1.sha1(passwordEdit.getTextString());
        }
        RequestParams params = new RequestParams();
        params.put("phone", phone);
        params.put("code", code);
        params.put("phoneCountryCode", phoneCountryCode);
        params.put("country", country);
        params.put("two_factor_code", two_factor_code);
        postNetwork(url, params, TAG_SET_USER_INFO);
        showProgressBar(true);
    }

    @Override
    public void parseJson(int code, JSONObject respanse, String tag, int pos, Object data) throws JSONException {
        if (tag.equals(TAG_SET_USER_INFO)) {
            showProgressBar(false, "");
            if (code == 0) {
                showMiddleToast("修改成功");
                setResult(Activity.RESULT_OK);
                user.setPhone(editPhone.getTextString(), pickCountry.getCountryCode());
                AccountInfo.saveAccount(this, user);
                MyApp.sUserObject = user;

                if (isFirstSet) {
                    popRewardDialog();
                } else {
                    finish();
                }
            } else {
                showProgressBar(false);
                showErrorMsgMiddle(code, respanse);
            }
        }
    }

    private void popRewardDialog() {
        View root = getLayoutInflater().inflate(R.layout.bind_phone_reward_dialog, null);
        new AlertDialog.Builder(this)
                .setView(root)
                .setCancelable(false)
                .show();

        root.findViewById(R.id.leftButton).setOnClickListener(v -> UserPointActivity_.intent(this).start());
        root.findViewById(R.id.rightButton).setOnClickListener(v -> ValidePhoneActivity.this.finish());
    }
}
