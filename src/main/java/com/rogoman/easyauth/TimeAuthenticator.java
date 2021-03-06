/*
 * Copyright 2015 Tomasz Rogozik
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.rogoman.easyauth;

import org.apache.commons.lang.StringUtils;

import java.security.InvalidKeyException;
import java.util.Calendar;
import java.util.Date;

/**
 * An authenticator implementation using time-based tokens.
 */
public class TimeAuthenticator extends Authenticator {

    /**
     * The number of milliseconds in a second.
     */
    public static final int MILLIS_IN_SECOND = 1000;

    /**
     * Default interval length in seconds. This is the default value for Google Authenticator.
     */
    public static final int DEFAULT_INTERVAL_IN_SECONDS = 30;

    private static final int CHECK_BACK_INTERVALS = 5;

    private static final int CHECK_FORWARD_INTERVALS = 5;

    private final UsedCodesManager<String> usedCodeManager;
    private final int intervalSeconds;

    /**
     * Creates a new instance of the TimeAuthenticator class.
     * You can pass your own used-codes manager and define a custom interval length.
     *
     * @param usedCodesManager used codes manager
     * @param intervalSeconds  token generating interval in seconds
     */
    public TimeAuthenticator(final UsedCodesManager<String> usedCodesManager, final int intervalSeconds) {
        if (usedCodesManager == null) {
            throw new IllegalArgumentException("usedCodesManager cannot be null");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds parameter has to be positive");
        }

        this.usedCodeManager = usedCodesManager;
        this.intervalSeconds = intervalSeconds;
    }

    /**
     * Creates a new instance of the TimeAuthenticator class.
     * The created authenticator uses a passed uses-codes manager and a default interval length compatible with Google Authenticator.
     * @param usedCodesManager used codes manager
     */
    public TimeAuthenticator(final UsedCodesManager<String> usedCodesManager) {
        this(usedCodesManager, DEFAULT_INTERVAL_IN_SECONDS);
    }

    /**
     * Creates a new instance of the TimeAuthenticator class.
     * The created authenticator uses a SimpleUsedCodesManager and a default interval length compatible with Google Authenticator.
     */
    public TimeAuthenticator() {
        this(new SimpleUsedCodesManager(), DEFAULT_INTERVAL_IN_SECONDS);
    }

    /**
     * Gets a new time-based code. Uses current datetime.
     *
     * @param secret secret used for generating the code
     * @return generated code
     * @exception java.security.InvalidKeyException if the secret passed has an invalid format
     * @exception com.rogoman.easyauth.AuthenticatorException if there is another problem in computing the code value
     */
    @Override
    public String getCode(final String secret) throws AuthenticatorException, InvalidKeyException {
        if (StringUtils.isEmpty(secret)) {
            throw new IllegalArgumentException("secret cannot be null");
        }
        long interval = getInterval(System.currentTimeMillis() / MILLIS_IN_SECOND);
        return this.getCodeInternal(secret, interval);
    }

    /**
     * Gets a new time-based code.
     *
     * @param secret                    secret used for generating the code
     * @param currentEpochTimeInSeconds current Epoch time in seconds
     * @return generated code
     * @exception java.security.InvalidKeyException if the secret passed has an invalid format
     * @exception com.rogoman.easyauth.AuthenticatorException if there is another problem in computing the code value
     */
    public String getCode(final String secret, final long currentEpochTimeInSeconds) throws AuthenticatorException, InvalidKeyException {
        if (StringUtils.isEmpty(secret)) {
            throw new IllegalArgumentException("secret cannot be null");
        }
        long interval = getInterval(currentEpochTimeInSeconds);
        return this.getCodeInternal(secret, interval);
    }

    /**
     * Checks if the provided code is valid for given secret key and user identifier. Current time is used for verification.
     *
     * @param secret         secret used for generating the code
     * @param code           generated code
     * @param userIdentifier user identifier
     * @return true if the code is valid and should be accepted
     */
    @Override
    public boolean checkCode(final String secret, final String code, final String userIdentifier) {
        if (StringUtils.isEmpty(secret)) {
            throw new IllegalArgumentException("secret cannot be null");
        }
        if (StringUtils.isEmpty(code)) {
            throw new IllegalArgumentException("code cannot be null");
        }

        Date baseTime = new Date();

        // We need to do this in constant time
        boolean codeMatch = false;
        for (int i = -CHECK_BACK_INTERVALS; i <= CHECK_FORWARD_INTERVALS; i++) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(baseTime);
            calendar.add(Calendar.SECOND, intervalSeconds * i);

            long currentEpochTime = calendar.getTimeInMillis() / MILLIS_IN_SECOND;
            long currentInterval = getInterval(currentEpochTime);

            try {
                if (stringEquals(getCode(secret, currentEpochTime), code)
                        && !usedCodeManager.isCodeUsed(currentInterval, code, userIdentifier)) {
                    codeMatch = true;
                    usedCodeManager.addCode(currentInterval, code, userIdentifier);
                    break;
                }
            } catch (final AuthenticatorException | InvalidKeyException e) {
                return false;
            }
        }
        return codeMatch;
    }

    protected UsedCodesManager<String> getUsedCodeManager() {
        return usedCodeManager;
    }

    protected int getIntervalSeconds() {
        return intervalSeconds;
    }

    /**
     * Gets an interval from a given Epoch time in seconds.
     *
     * @param epochTimeInSeconds Epoch time in seconds
     * @return Interval to be used in calculations
     */
    private long getInterval(final long epochTimeInSeconds) {
        return epochTimeInSeconds / intervalSeconds;
    }
}
