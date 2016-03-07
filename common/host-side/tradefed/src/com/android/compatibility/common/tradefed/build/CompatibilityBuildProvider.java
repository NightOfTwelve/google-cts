/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.build;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
/**
 * A simple {@link IBuildProvider} that uses a pre-existing Compatibility install.
 */
@OptionClass(alias="compatibility-build-provider")
public class CompatibilityBuildProvider implements IBuildProvider {

    @Option(name="branch", description="build branch name to supply.")
    private String mBranch = null;

    private static final String PACKAGE_NAME = "com.android.compatibility.common.tradefed.build";

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() {
        // Create a blank BuildInfo which will get populated later.
        String version = Package.getPackage(PACKAGE_NAME).getImplementationVersion();
        if (version == null) {
            version = IBuildInfo.UNKNOWN_BUILD_ID;
        }
        IBuildInfo ctsBuild = new BuildInfo(version, "cts", "cts");
        if (mBranch  != null) {
            ctsBuild.setBuildBranch(mBranch);
        }

        return ctsBuild;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        // ignore
    }
}
