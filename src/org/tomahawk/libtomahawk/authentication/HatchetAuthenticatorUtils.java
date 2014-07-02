/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2012, Christopher Reichert <creichert07@gmail.com>
 *   Copyright 2013, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.libtomahawk.authentication;

import org.json.JSONException;
import org.json.JSONObject;
import org.tomahawk.libtomahawk.collection.CollectionManager;
import org.tomahawk.libtomahawk.infosystem.InfoRequestData;
import org.tomahawk.libtomahawk.infosystem.InfoSystem;
import org.tomahawk.libtomahawk.infosystem.User;
import org.tomahawk.libtomahawk.utils.TomahawkUtils;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.TomahawkApp;
import org.tomahawk.tomahawk_android.utils.ThreadManager;
import org.tomahawk.tomahawk_android.utils.TomahawkRunnable;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class HatchetAuthenticatorUtils extends AuthenticatorUtils {

    private static final String TAG = HatchetAuthenticatorUtils.class.getSimpleName();

    public static final String AUTH_SERVER = "https://auth.hatchet.is/v1/authentication/password";

    public static final String REFRESH_TOKEN_SERVER
            = "https://auth.hatchet.is/v1/tokens/refresh/bearer";

    public static final String TOKEN_SERVER = "https://auth.hatchet.is/v1/tokens/fetch/";

    public static final String PARAMS_GRANT_TYPE = "grant_type";

    public static final String PARAMS_GRANT_TYPE_PASSWORD = "password";

    public static final String PARAMS_GRANT_TYPE_REFRESHTOKEN = "refresh_token";

    public static final String PARAMS_AUTHORIZATION = "authorization";

    public static final String PARAMS_USERNAME = "username";

    public static final String PARAMS_PASSWORD = "password";

    public static final String PARAMS_REFRESHTOKEN = "refresh_token";

    public static final String RESPONSE_ACCESS_TOKEN = "access_token";

    public static final String RESPONSE_CANONICAL_USERNAME = "canonical_username";

    public static final String RESPONSE_EXPIRES_IN = "expires_in";

    public static final String RESPONSE_REFRESH_TOKEN = "refresh_token";

    public static final String RESPONSE_REFRESH_TOKEN_EXPIRES_IN = "refresh_token_expires_in";

    public static final String RESPONSE_TOKEN_TYPE = "token_type";

    public static final String RESPONSE_TOKEN_TYPE_BEARER = "bearer";

    public static final String RESPONSE_TOKEN_TYPE_CALUMET = "calumet";

    public static final String RESPONSE_ERROR = "error";

    public static final String RESPONSE_ERROR_INVALID_REQUEST = "invalid_request";

    public static final String RESPONSE_ERROR_INVALID_CLIENT = "invalid_client";

    public static final String RESPONSE_ERROR_INVALID_GRANT = "invalid_grant";

    public static final String RESPONSE_ERROR_UNAUTHORIZED_CLIENT = "unauthorized_client";

    public static final String RESPONSE_ERROR_UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";

    public static final String RESPONSE_ERROR_INVALID_SCOPE = "invalid_scope";

    public static final String RESPONSE_ERROR_DESCRIPTION = "error_description";

    public static final String RESPONSE_ERROR_URI = "error_uri";

    private static final int EXPIRING_LIMIT = 300;

    private User mLoggedInUser;

    public HatchetAuthenticatorUtils(String prettyName) {
        super(prettyName);
        onInit();
    }

    @Override
    public void onInit() {
    }

    @Override
    public void onLogin(String username) {
        Log.d(TAG,
                "TomahawkService: Hatchet user '" + username + "' logged in successfully :)");
    }

    @Override
    public void onLoginFailed(final String message) {
        Log.d(TAG, "TomahawkService: Hatchet login failed :(, Error: " + message);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TomahawkApp.getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
        mIsAuthenticating = false;
        AuthenticatorManager.getInstance()
                .onLoggedInOut(TomahawkApp.PLUGINNAME_HATCHET, false);
    }

    @Override
    public void onLogout() {
        Log.d(TAG, "TomahawkService: Hatchet user logged out");
        mIsAuthenticating = false;
        AuthenticatorManager.getInstance().onLoggedInOut(
                TomahawkApp.PLUGINNAME_HATCHET, false);
    }

    @Override
    public void onAuthTokenProvided(String username, String refreshToken,
            int refreshTokenExpiresIn, String accessToken, int accessTokenExpiresIn) {
        if (username != null && !TextUtils.isEmpty(username) && refreshToken != null
                && !TextUtils.isEmpty(refreshToken)) {
            Log.d(TAG, "TomahawkService: Hatchet auth token is served and yummy");
            Account account = new Account(username,
                    TomahawkApp.getContext().getString(R.string.accounttype_string));
            AccountManager am = AccountManager.get(TomahawkApp.getContext());
            if (am != null) {
                am.addAccountExplicitly(account, null, new Bundle());
                am.setUserData(account, AuthenticatorUtils.AUTHENTICATOR_NAME,
                        getAuthenticatorUtilsName());
                am.setAuthToken(account, AuthenticatorUtils.AUTH_TOKEN_TYPE_HATCHET,
                        refreshToken);
                am.setUserData(account, AuthenticatorUtils.AUTH_TOKEN_EXPIRES_IN_HATCHET,
                        String.valueOf(refreshTokenExpiresIn));
                am.setUserData(account, AuthenticatorUtils.MANDELLA_ACCESS_TOKEN_HATCHET,
                        accessToken);
                am.setUserData(account,
                        AuthenticatorUtils.MANDELLA_ACCESS_TOKEN_EXPIRATIONTIME_HATCHET,
                        String.valueOf(accessTokenExpiresIn));
                ensureAccessTokens();
            }
        }
        CollectionManager.getInstance().fetchHatchetUserPlaylists();
        CollectionManager.getInstance().fetchLovedItemsUserPlaylists();
        CollectionManager.getInstance().fetchStarredArtists();
        CollectionManager.getInstance().fetchStarredAlbums();
        InfoSystem.getInstance().resolve(InfoRequestData.INFOREQUESTDATA_TYPE_USERS_SELF,
                null);
        mIsAuthenticating = false;
        AuthenticatorManager.getInstance().onLoggedInOut(
                TomahawkApp.PLUGINNAME_HATCHET, true);
    }

    @Override
    public int getTitleResourceId() {
        return R.string.authenticator_title_hatchet;
    }

    @Override
    public int getIconResourceId() {
        return R.drawable.hatchet_icon;
    }

    @Override
    public String getAuthenticatorUtilsName() {
        return AuthenticatorUtils.AUTHENTICATOR_NAME_HATCHET;
    }

    @Override
    public String getAuthenticatorUtilsTokenType() {
        return AuthenticatorUtils.AUTH_TOKEN_TYPE_HATCHET;
    }

    @Override
    public int getUserIdEditTextHintResId() {
        return R.string.logindialog_username_label_string;
    }

    @Override
    public void login(Activity activity, final String name, final String password) {
        mIsAuthenticating = true;
        ThreadManager.getInstance().execute(
                new TomahawkRunnable(TomahawkRunnable.PRIORITY_IS_AUTHENTICATING) {
                    @Override
                    public void run() {
                        Map<String, String> dataMap = new HashMap<String, String>();
                        dataMap.put(PARAMS_PASSWORD, password);
                        dataMap.put(PARAMS_USERNAME, name);
                        dataMap.put(PARAMS_GRANT_TYPE, PARAMS_GRANT_TYPE_PASSWORD);
                        String data = TomahawkUtils.paramsListToString(dataMap);
                        try {
                            String jsonString = TomahawkUtils.httpPost(AUTH_SERVER, null, data,
                                    TomahawkUtils.HTTP_CONTENT_TYPE_FORM);
                            JSONObject jsonObject = new JSONObject(jsonString);
                            if (jsonObject.has(RESPONSE_ERROR)) {
                                String error = jsonObject.getString(RESPONSE_ERROR);
                                String errorDescription = "";
                                if (jsonObject.has(RESPONSE_ERROR_DESCRIPTION)) {
                                    errorDescription += jsonObject.getString(
                                            RESPONSE_ERROR_DESCRIPTION);
                                }
                                if (jsonObject.has(RESPONSE_ERROR_URI)) {
                                    errorDescription += ", URI: " + jsonObject
                                            .getString(RESPONSE_ERROR_URI);
                                }
                                onLoginFailed(errorDescription);
                            } else if (jsonObject.has(RESPONSE_ACCESS_TOKEN) && jsonObject.has(
                                    RESPONSE_CANONICAL_USERNAME) && jsonObject
                                    .has(RESPONSE_EXPIRES_IN)
                                    && jsonObject.has(RESPONSE_REFRESH_TOKEN) && jsonObject.has(
                                    RESPONSE_REFRESH_TOKEN_EXPIRES_IN) && jsonObject.has(
                                    RESPONSE_TOKEN_TYPE)) {
                                String username = jsonObject.getString(RESPONSE_CANONICAL_USERNAME);
                                String refreshtoken = jsonObject.getString(RESPONSE_REFRESH_TOKEN);
                                int refreshTokenExpiresIn = jsonObject
                                        .getInt(RESPONSE_REFRESH_TOKEN_EXPIRES_IN);
                                String accessToken = jsonObject.getString(RESPONSE_ACCESS_TOKEN);
                                int accessTokenExpiresIn = jsonObject.getInt(RESPONSE_EXPIRES_IN);
                                onLogin(username);
                                onAuthTokenProvided(username, refreshtoken,
                                        refreshTokenExpiresIn, accessToken, accessTokenExpiresIn);
                            } else {
                                onLoginFailed("Unknown error");
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "login: " + e.getClass() + ": " + e.getLocalizedMessage());
                            onLoginFailed(e.getMessage());
                        } catch (UnsupportedEncodingException e) {
                            Log.e(TAG, "login: " + e.getClass() + ": " + e.getLocalizedMessage());
                            onLoginFailed(e.getMessage());
                        } catch (IOException e) {
                            Log.e(TAG, "login: " + e.getClass() + ": " + e.getLocalizedMessage());
                            onLoginFailed(e.getMessage());
                        } catch (NoSuchAlgorithmException e) {
                            Log.e(TAG, "login: " + e.getClass() + ": " + e.getLocalizedMessage());
                            onLoginFailed(e.getMessage());
                        } catch (KeyManagementException e) {
                            Log.e(TAG, "login: " + e.getClass() + ": " + e.getLocalizedMessage());
                            onLoginFailed(e.getMessage());
                        }
                    }
                }
        );
    }

    @Override
    public void logout(Activity activity) {
        mIsAuthenticating = true;
        final AccountManager am = AccountManager.get(TomahawkApp.getContext());
        Account account = TomahawkUtils.getAccountByName(getAuthenticatorUtilsName());
        if (am != null && account != null) {
            am.removeAccount(account, null, null);
        }
        mLoggedInUser = null;
        onLogout();
    }

    /**
     * Ensure that the calumet access token is available and valid. Get it from the cache if it
     * hasn't yet expired. Otherwise refetch and cache it again. Also refetches the mandella access
     * token if necessary.
     *
     * @return the calumet access token
     */
    public String ensureAccessTokens() {
        Map<String, String> userData = new HashMap<String, String>();
        userData.put(AuthenticatorUtils.MANDELLA_ACCESS_TOKEN_HATCHET, null);
        userData.put(AuthenticatorUtils.MANDELLA_ACCESS_TOKEN_EXPIRATIONTIME_HATCHET, null);
        userData.put(AuthenticatorUtils.CALUMET_ACCESS_TOKEN_HATCHET, null);
        userData.put(AuthenticatorUtils.CALUMET_ACCESS_TOKEN_EXPIRATIONTIME_HATCHET, null);
        userData = TomahawkUtils
                .getUserDataForAccount(userData, getAuthenticatorUtilsName());
        String mandellaAccessToken = userData.get(AuthenticatorUtils.MANDELLA_ACCESS_TOKEN_HATCHET);
        int mandellaExpirationTime = -1;
        String mandellaExpirationTimeString =
                userData.get(AuthenticatorUtils.MANDELLA_ACCESS_TOKEN_EXPIRATIONTIME_HATCHET);
        if (mandellaExpirationTimeString != null) {
            mandellaExpirationTime = Integer.valueOf(mandellaExpirationTimeString);
        }
        String calumetAccessToken = userData.get(AuthenticatorUtils.CALUMET_ACCESS_TOKEN_HATCHET);
        int calumetExpirationTime = -1;
        String calumetExpirationTimeString =
                userData.get(AuthenticatorUtils.CALUMET_ACCESS_TOKEN_EXPIRATIONTIME_HATCHET);
        if (calumetExpirationTimeString != null) {
            calumetExpirationTime = Integer.valueOf(mandellaExpirationTimeString);
        }
        int currentTime = (int) (System.currentTimeMillis() / 1000);
        String refreshToken = TomahawkUtils
                .peekAuthTokenForAccount(getAuthenticatorUtilsName(),
                        getAuthenticatorUtilsTokenType());
        if (refreshToken != null && (mandellaAccessToken == null
                || currentTime > mandellaExpirationTime - EXPIRING_LIMIT)) {
            Log.d(TAG, "Mandella access token has expired, refreshing ...");
            mandellaAccessToken = fetchAccessToken(RESPONSE_TOKEN_TYPE_BEARER, refreshToken);
        }
        if (mandellaAccessToken != null && (calumetAccessToken == null
                || currentTime > calumetExpirationTime - EXPIRING_LIMIT)) {
            Log.d(TAG, "Calumet access token has expired, refreshing ...");
            calumetAccessToken = fetchAccessToken(RESPONSE_TOKEN_TYPE_CALUMET, mandellaAccessToken);
        }
        if (calumetAccessToken == null) {
            Log.d(TAG, "Calumet access token couldn't be fetched. "
                    + "Most probably because no Hatchet account is logged in.");
        }
        return calumetAccessToken;
    }

    /**
     * Fetch the accessToken of the given tokenType by providing an existent token. The token is
     * cached and then returned.
     *
     * @param tokenType The token type ("bearer"(aka mandella) or "calumet")
     * @param token     In the case of fetching the bearer token, this token should be the bearer
     *                  refresh token provided by the initial auth process. If the calumet access
     *                  token should be fetched, then the given token should be the bearer access
     *                  token.
     * @return the fetched access token
     */
    public String fetchAccessToken(String tokenType, String token) {
        String accessToken = null;
        try {
            String jsonString;
            if (tokenType.equals(RESPONSE_TOKEN_TYPE_BEARER)) {
                Map<String, String> dataMap = new HashMap<String, String>();
                dataMap.put(PARAMS_REFRESHTOKEN, token);
                dataMap.put(PARAMS_GRANT_TYPE, PARAMS_GRANT_TYPE_REFRESHTOKEN);
                String data = TomahawkUtils.paramsListToString(dataMap);
                jsonString = TomahawkUtils.httpPost(REFRESH_TOKEN_SERVER, null, data,
                        TomahawkUtils.HTTP_CONTENT_TYPE_FORM);
            } else {
                Map<String, String> params = new HashMap<String, String>();
                params.put(PARAMS_AUTHORIZATION, RESPONSE_TOKEN_TYPE_BEARER + " " + token);
                jsonString = TomahawkUtils.httpGet(TOKEN_SERVER + RESPONSE_TOKEN_TYPE_CALUMET,
                        params);
            }
            JSONObject jsonObject = new JSONObject(jsonString);
            if (jsonObject.has(RESPONSE_ERROR) || !TomahawkUtils
                    .containsIgnoreCase(tokenType, jsonObject.getString(RESPONSE_TOKEN_TYPE))) {
                String errorDescription = "Please reenter your Hatchet credentials";
                logout();
                onLoginFailed(errorDescription);
            } else if (jsonObject.has(RESPONSE_ACCESS_TOKEN)
                    && jsonObject.has(RESPONSE_EXPIRES_IN)) {
                Map<String, String> data = new HashMap<String, String>();
                accessToken = jsonObject.getString(RESPONSE_ACCESS_TOKEN);
                int expiresIn = jsonObject.getInt(RESPONSE_EXPIRES_IN);
                int currentTime = (int) (System.currentTimeMillis() / 1000);
                int expirationTime = currentTime + expiresIn;
                Log.d(TAG, "Access token fetched, current time: '" + currentTime +
                        "', expiration time: '" + expirationTime + "'");
                if (TomahawkUtils.containsIgnoreCase(tokenType, RESPONSE_TOKEN_TYPE_BEARER)) {
                    data.put(AuthenticatorUtils.MANDELLA_ACCESS_TOKEN_HATCHET, accessToken);
                    data.put(AuthenticatorUtils.MANDELLA_ACCESS_TOKEN_EXPIRATIONTIME_HATCHET,
                            String.valueOf(expirationTime));
                } else {
                    data.put(AuthenticatorUtils.CALUMET_ACCESS_TOKEN_HATCHET, accessToken);
                    data.put(AuthenticatorUtils.CALUMET_ACCESS_TOKEN_EXPIRATIONTIME_HATCHET,
                            String.valueOf(expirationTime));
                }
                TomahawkUtils.setUserDataForAccount(data, getAuthenticatorUtilsName());
            }
        } catch (JSONException e) {
            Log.e(TAG,
                    "fetchAccessToken: " + e.getClass() + ": " + e.getLocalizedMessage());
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG,
                    "fetchAccessToken: " + e.getClass() + ": " + e.getLocalizedMessage());
        } catch (IOException e) {
            Log.e(TAG,
                    "fetchAccessToken: " + e.getClass() + ": " + e.getLocalizedMessage());
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG,
                    "fetchAccessToken: " + e.getClass() + ": " + e.getLocalizedMessage());
        } catch (KeyManagementException e) {
            Log.e(TAG,
                    "fetchAccessToken: " + e.getClass() + ": " + e.getLocalizedMessage());
        }
        return accessToken;
    }

    public User getLoggedInUser() {
        return mLoggedInUser;
    }

    public void setLoggedInUser(User loggedInUser) {
        mLoggedInUser = loggedInUser;
    }
}

