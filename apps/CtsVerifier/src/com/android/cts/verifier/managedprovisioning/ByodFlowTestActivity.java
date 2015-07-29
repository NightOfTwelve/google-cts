/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListActivity;
import com.android.cts.verifier.TestResult;

/**
 * CTS verifier test for BYOD managed provisioning flow.
 * This activity is responsible for starting the managed provisioning flow and verify the outcome of provisioning.
 * It performs the following verifications:
 *   Full disk encryption is enabled.
 *   Profile owner is correctly installed.
 *   Profile owner shows up in the Settings app.
 *   Badged work apps show up in launcher.
 * The first two verifications are performed automatically, by interacting with profile owner using
 * cross-profile intents, while the last two are carried out manually by the user.
 */
public class ByodFlowTestActivity extends DialogTestListActivity {

    private final String TAG = "ByodFlowTestActivity";
    private static final int REQUEST_MANAGED_PROVISIONING = 0;
    private static final int REQUEST_PROFILE_OWNER_STATUS = 1;
    private static final int REQUEST_INTENT_FILTERS_STATUS = 2;

    private ComponentName mAdminReceiverComponent;

    private DialogTestListItem mProfileOwnerInstalled;
    private DialogTestListItem mProfileAccountVisibleTest;
    private DialogTestListItem mDeviceAdminVisibleTest;
    private DialogTestListItem mWorkAppVisibleTest;
    private DialogTestListItem mCrossProfileIntentFiltersTest;
    private DialogTestListItem mDisableNonMarketTest;
    private DialogTestListItem mEnableNonMarketTest;
    private DialogTestListItem mWorkNotificationBadgedTest;
    private DialogTestListItem mWorkStatusBarIconTest;
    private DialogTestListItem mWorkStatusBarToastTest;
    private DialogTestListItem mAppSettingsVisibleTest;
    private DialogTestListItem mLocationSettingsVisibleTest;
    private DialogTestListItem mCredSettingsVisibleTest;
    private DialogTestListItem mPrintSettingsVisibleTest;
    private DialogTestListItem mIntentFiltersTest;
    private DialogTestListItem mCrossProfileImageCaptureSupportTest;
    private DialogTestListItem mCrossProfileVideoCaptureSupportTest;
    private DialogTestListItem mCrossProfileAudioCaptureSupportTest;

    public ByodFlowTestActivity() {
        super(R.layout.provisioning_byod,
                R.string.provisioning_byod, R.string.provisioning_byod_info,
                R.string.provisioning_byod_instructions);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdminReceiverComponent = new ComponentName(this, DeviceAdminTestReceiver.class.getName());

        disableComponent();
        mPrepareTestButton.setText(R.string.provisioning_byod_start);
        mPrepareTestButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startByodProvisioning();
            }
        });

        // If we are started by managed provisioning (fresh managed provisioning after encryption
        // reboot), redirect the user back to the main test list. This is because the test result
        // is only saved by the parent TestListActivity, and if we did allow the user to proceed
        // here, the test result would be lost when this activity finishes.
        if (ByodHelperActivity.ACTION_PROFILE_OWNER_STATUS.equals(getIntent().getAction())) {
            startActivity(new Intent(this, TestListActivity.class));
            // Calling super.finish() because we delete managed profile in our overridden of finish(),
            // which is not what we want to do here.
            super.finish();
        } else {
            queryProfileOwner(false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // This is called when managed provisioning completes successfully without reboot.
        super.onNewIntent(intent);
        if (ByodHelperActivity.ACTION_PROFILE_OWNER_STATUS.equals(intent.getAction())) {
            handleStatusUpdate(RESULT_OK, intent);
        }
    }

    @Override
    protected void handleActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_MANAGED_PROVISIONING:
                return;
            case REQUEST_PROFILE_OWNER_STATUS: {
                // Called after queryProfileOwner()
                handleStatusUpdate(resultCode, data);
            } break;
            case REQUEST_INTENT_FILTERS_STATUS: {
                // Called after checkIntentFilters()
                handleIntentFiltersStatus(resultCode);
            } break;
            default: {
                super.handleActivityResult(requestCode, resultCode, data);
            }
        }
    }

    private void handleStatusUpdate(int resultCode, Intent data) {
        boolean provisioned = data != null &&
                data.getBooleanExtra(ByodHelperActivity.EXTRA_PROVISIONED, false);
        setTestResult(mProfileOwnerInstalled, (provisioned && resultCode == RESULT_OK) ?
                TestResult.TEST_RESULT_PASSED : TestResult.TEST_RESULT_FAILED);
    }

    @Override
    public void finish() {
        // Pass and fail buttons are known to call finish() when clicked, and this is when we want to
        // clean up the provisioned profile.
        requestDeleteProfileOwner();
        super.finish();
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        mProfileOwnerInstalled = new DialogTestListItem(this,
                R.string.provisioning_byod_profileowner,
                "BYOD_ProfileOwnerInstalled") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                queryProfileOwner(true);
            }
        };

        /*
         * To keep the image in this test up to date, use the instructions in
         * {@link ByodIconSamplerActivity}.
         */
        mWorkAppVisibleTest = new DialogTestListItemWithIcon(this,
                R.string.provisioning_byod_workapps_visible,
                "BYOD_WorkAppVisibleTest",
                R.string.provisioning_byod_workapps_visible_instruction,
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                R.drawable.badged_icon);

        mWorkNotificationBadgedTest = new DialogTestListItemWithIcon(this,
                R.string.provisioning_byod_work_notification,
                "BYOD_WorkNotificationBadgedTest",
                R.string.provisioning_byod_work_notification_instruction,
                new Intent(WorkNotificationTestActivity.ACTION_WORK_NOTIFICATION),
                R.drawable.ic_corp_icon);

        Intent workStatusIcon = new Intent(WorkStatusTestActivity.ACTION_WORK_STATUS_ICON);
        workStatusIcon.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mWorkStatusBarIconTest = new DialogTestListItemWithIcon(this,
                R.string.provisioning_byod_work_status_icon,
                "BYOD_WorkStatusBarIconTest",
                R.string.provisioning_byod_work_status_icon_instruction,
                workStatusIcon,
                R.drawable.stat_sys_managed_profile_status);

        Intent workStatusToast = new Intent(WorkStatusTestActivity.ACTION_WORK_STATUS_TOAST);
        workStatusToast.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mWorkStatusBarToastTest = new DialogTestListItem(this,
                R.string.provisioning_byod_work_status_toast,
                "BYOD_WorkStatusBarToastTest",
                R.string.provisioning_byod_work_status_toast_instruction,
                workStatusToast);

        mDisableNonMarketTest = new DialogTestListItem(this,
                R.string.provisioning_byod_nonmarket_deny,
                "BYOD_DisableNonMarketTest",
                R.string.provisioning_byod_nonmarket_deny_info,
                new Intent(ByodHelperActivity.ACTION_INSTALL_APK)
                        .putExtra(ByodHelperActivity.EXTRA_ALLOW_NON_MARKET_APPS, false));

        mEnableNonMarketTest = new DialogTestListItem(this,
                R.string.provisioning_byod_nonmarket_allow,
                "BYOD_EnableNonMarketTest",
                R.string.provisioning_byod_nonmarket_allow_info,
                new Intent(ByodHelperActivity.ACTION_INSTALL_APK)
                        .putExtra(ByodHelperActivity.EXTRA_ALLOW_NON_MARKET_APPS, true));

        mProfileAccountVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_profile_visible,
                "BYOD_ProfileAccountVisibleTest",
                R.string.provisioning_byod_profile_visible_instruction,
                new Intent(Settings.ACTION_SETTINGS));

        mAppSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_app_settings,
                "BYOD_AppSettingsVisibleTest",
                R.string.provisioning_byod_app_settings_instruction,
                new Intent(Settings.ACTION_APPLICATION_SETTINGS));

        mDeviceAdminVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_admin_visible,
                "BYOD_DeviceAdminVisibleTest",
                R.string.provisioning_byod_admin_visible_instruction,
                new Intent(Settings.ACTION_SECURITY_SETTINGS));

        mCredSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_cred_settings,
                "BYOD_CredSettingsVisibleTest",
                R.string.provisioning_byod_cred_settings_instruction,
                new Intent(Settings.ACTION_SECURITY_SETTINGS));

        mLocationSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_location_settings,
                "BYOD_LocationSettingsVisibleTest",
                R.string.provisioning_byod_location_settings_instruction,
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));

        mPrintSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_print_settings,
                "BYOD_PrintSettingsVisibleTest",
                R.string.provisioning_byod_print_settings_instruction,
                new Intent(Settings.ACTION_PRINT_SETTINGS));

        Intent intent = new Intent(CrossProfileTestActivity.ACTION_CROSS_PROFILE);
        Intent chooser = Intent.createChooser(intent,
                getResources().getString(R.string.provisioning_cross_profile_chooser));
        mCrossProfileIntentFiltersTest = new DialogTestListItem(this,
                R.string.provisioning_byod_cross_profile,
                "BYOD_CrossProfileIntentFiltersTest",
                R.string.provisioning_byod_cross_profile_instruction,
                chooser);

        // Test for checking if the required intent filters are set during managed provisioning.
        mIntentFiltersTest = new DialogTestListItem(this,
                R.string.provisioning_byod_cross_profile_intent_filters,
                "BYOD_IntentFiltersTest") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                checkIntentFilters();
            }
        };

        adapter.add(mProfileOwnerInstalled);

        // Badge related tests
        adapter.add(mWorkAppVisibleTest);
        adapter.add(mWorkNotificationBadgedTest);
        adapter.add(mWorkStatusBarIconTest);
        adapter.add(mWorkStatusBarToastTest);

        // Settings related tests.
        adapter.add(mProfileAccountVisibleTest);
        adapter.add(mDeviceAdminVisibleTest);
        adapter.add(mCredSettingsVisibleTest);
        adapter.add(mAppSettingsVisibleTest);
        adapter.add(mLocationSettingsVisibleTest);
        adapter.add(mPrintSettingsVisibleTest);

        adapter.add(mCrossProfileIntentFiltersTest);
        adapter.add(mDisableNonMarketTest);
        adapter.add(mEnableNonMarketTest);
        adapter.add(mIntentFiltersTest);

        if (canResolveIntent(ByodHelperActivity.getCaptureImageIntent())) {
            // Capture image intent can be resolved in primary profile, so test.
            mCrossProfileImageCaptureSupportTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_capture_image_support,
                    "BYOD_CrossProfileImageCaptureSupportTest",
                    R.string.provisioning_byod_capture_image_support_info,
                    new Intent(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_IMAGE));
            adapter.add(mCrossProfileImageCaptureSupportTest);
        } else {
            // Capture image intent cannot be resolved in primary profile, so skip test.
            Toast.makeText(ByodFlowTestActivity.this,
                    R.string.provisioning_byod_no_image_capture_resolver, Toast.LENGTH_SHORT)
                    .show();
        }

        if (canResolveIntent(ByodHelperActivity.getCaptureVideoIntent())) {
            // Capture video intent can be resolved in primary profile, so test.
            mCrossProfileVideoCaptureSupportTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_capture_video_support,
                    "BYOD_CrossProfileVideoCaptureSupportTest",
                    R.string.provisioning_byod_capture_video_support_info,
                    new Intent(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_VIDEO));
            adapter.add(mCrossProfileVideoCaptureSupportTest);
        } else {
            // Capture video intent cannot be resolved in primary profile, so skip test.
            Toast.makeText(ByodFlowTestActivity.this,
                    R.string.provisioning_byod_no_video_capture_resolver, Toast.LENGTH_SHORT)
                    .show();
        }

        /* TODO: reinstate when bug b/20131958 is fixed
        if (canResolveIntent(ByodHelperActivity.getCaptureAudioIntent())) {
            // Capture audio intent can be resolved in primary profile, so test.
            mCrossProfileAudioCaptureSupportTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_capture_audio_support,
                    "BYOD_CrossProfileAudioCaptureSupportTest",
                    R.string.provisioning_byod_capture_audio_support_info,
                    new Intent(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_AUDIO));
            adapter.add(mCrossProfileAudioCaptureSupportTest);
        } else {
            // Capture audio intent cannot be resolved in primary profile, so skip test.
            Toast.makeText(ByodFlowTestActivity.this,
                    R.string.provisioning_byod_no_audio_capture_resolver, Toast.LENGTH_SHORT)
                    .show();
        }
        */
    }

    // Return whether the intent can be resolved in the current profile
    private boolean canResolveIntent(Intent intent) {
        return intent.resolveActivity(getPackageManager()) != null;
    }

    @Override
    protected void clearRemainingState(final DialogTestListItem test) {
        super.clearRemainingState(test);
        if (WorkNotificationTestActivity.ACTION_WORK_NOTIFICATION.equals(
                test.getManualTestIntent().getAction())) {
            try {
                startActivity(new Intent(
                        WorkNotificationTestActivity.ACTION_CLEAR_WORK_NOTIFICATION));
            } catch (ActivityNotFoundException e) {
                // User shouldn't run this test before work profile is set up.
            }
        }
    }

    private void startByodProvisioning() {
        Intent sending = new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        sending.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                mAdminReceiverComponent);

        if (sending.resolveActivity(getPackageManager()) != null) {
            // ManagedProvisioning must be started with startActivityForResult, but we don't
            // care about the result, so passing 0 as a requestCode
            startActivityForResult(sending, REQUEST_MANAGED_PROVISIONING);
        } else {
            showToast(R.string.provisioning_byod_disabled);
        }
    }

    private void queryProfileOwner(boolean showToast) {
        try {
            Intent intent = new Intent(ByodHelperActivity.ACTION_QUERY_PROFILE_OWNER);
            startActivityForResult(intent, REQUEST_PROFILE_OWNER_STATUS);
        }
        catch (ActivityNotFoundException e) {
            Log.d(TAG, "queryProfileOwner: ActivityNotFoundException", e);
            setTestResult(mProfileOwnerInstalled, TestResult.TEST_RESULT_FAILED);
            if (showToast) {
                showToast(R.string.provisioning_byod_no_activity);
            }
        }
    }

    private void requestDeleteProfileOwner() {
        try {
            Intent intent = new Intent(ByodHelperActivity.ACTION_REMOVE_PROFILE_OWNER);
            startActivity(intent);
            showToast(R.string.provisioning_byod_delete_profile);
        }
        catch (ActivityNotFoundException e) {
            Log.d(TAG, "requestDeleteProfileOwner: ActivityNotFoundException", e);
        }
    }

    private void checkIntentFilters() {
        try {
            // We disable the ByodHelperActivity in the primary profile. So, this intent
            // will be handled by the ByodHelperActivity in the managed profile.
            Intent intent = new Intent(ByodHelperActivity.ACTION_CHECK_INTENT_FILTERS);
            startActivityForResult(intent, REQUEST_INTENT_FILTERS_STATUS);
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "checkIntentFilters: ActivityNotFoundException", e);
            setTestResult(mIntentFiltersTest, TestResult.TEST_RESULT_FAILED);
            showToast(R.string.provisioning_byod_no_activity);
        }
    }

    private void handleIntentFiltersStatus(int resultCode) {
        // we use the resultCode from ByodHelperActivity in the managed profile to know if certain
        // intents fired from the managed profile are forwarded.
        final boolean intentFiltersSetForManagedIntents = (resultCode == RESULT_OK);
        // Since the ByodFlowTestActivity is running in the primary profile, we directly use
        // the IntentFiltersTestHelper to know if certain intents fired from the primary profile
        // are forwarded.
        final boolean intentFiltersSetForPrimaryIntents =
                new IntentFiltersTestHelper(this).checkCrossProfileIntentFilters(
                        IntentFiltersTestHelper.FLAG_INTENTS_FROM_PRIMARY);
        final boolean intentFiltersSet =
                intentFiltersSetForPrimaryIntents & intentFiltersSetForManagedIntents;
        setTestResult(mIntentFiltersTest,
                intentFiltersSet ? TestResult.TEST_RESULT_PASSED : TestResult.TEST_RESULT_FAILED);
    }

    private void disableComponent() {
        // Disable app components in the current profile, so only the counterpart in the other profile
        // can respond (via cross-profile intent filter)
        getPackageManager().setComponentEnabledSetting(new ComponentName(
                this, ByodHelperActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        getPackageManager().setComponentEnabledSetting(new ComponentName(
                this, WorkNotificationTestActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        getPackageManager().setComponentEnabledSetting(new ComponentName(
                this, WorkStatusTestActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}
