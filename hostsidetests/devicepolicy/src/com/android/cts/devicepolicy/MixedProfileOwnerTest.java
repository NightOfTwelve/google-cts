/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.devicepolicy;

/**
 * Set of tests for pure (non-managed) profile owner use cases that also apply to device owners.
 * Tests that should be run identically in both cases are added in DeviceAndProfileOwnerTest.
 */
public class MixedProfileOwnerTest extends DeviceAndProfileOwnerTest {

    protected static final String CLEAR_PROFILE_OWNER_TEST_CLASS =
            DEVICE_ADMIN_PKG + ".ClearProfileOwnerTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (mHasFeature) {
            mUserId = USER_OWNER;

            installAppAsUser(DEVICE_ADMIN_APK, mUserId);
            setProfileOwnerOrFail(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            assertTrue("Failed to remove profile owner.",
                    runDeviceTests(DEVICE_ADMIN_PKG, CLEAR_PROFILE_OWNER_TEST_CLASS));
        }
        super.tearDown();
    }
}
