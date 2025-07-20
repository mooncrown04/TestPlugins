2025-07-20T15:07:24.5939652Z [36;1m./gradlew make makePluginsJson[0m
2025-07-20T15:07:24.5939927Z [36;1mcp **/build/*.cs3 $GITHUB_WORKSPACE/builds[0m
2025-07-20T15:07:24.5940242Z [36;1mcp build/plugins.json $GITHUB_WORKSPACE/builds[0m
2025-07-20T15:07:24.5969023Z shell: /usr/bin/bash -e {0}
2025-07-20T15:07:24.5969247Z env:
2025-07-20T15:07:24.5969528Z   JAVA_HOME: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17.0.15-6/x64
2025-07-20T15:07:24.5969979Z   JAVA_HOME_17_X64: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17.0.15-6/x64
2025-07-20T15:07:24.5970341Z   ANDROID_HOME: /usr/local/lib/android/sdk
2025-07-20T15:07:24.5970776Z   ANDROID_SDK_ROOT: /usr/local/lib/android/sdk
2025-07-20T15:07:24.5971013Z ##[endgroup]
2025-07-20T15:07:24.6968202Z Downloading https://services.gradle.org/distributions/gradle-8.12-bin.zip
2025-07-20T15:07:26.0273762Z ..................................................................................................................................
2025-07-20T15:07:26.0277418Z Unzipping /home/runner/.gradle/wrapper/dists/gradle-8.12-bin/cetblhg4pflnnks72fxwobvgv/gradle-8.12-bin.zip to /home/runner/.gradle/wrapper/dists/gradle-8.12-bin/cetblhg4pflnnks72fxwobvgv
2025-07-20T15:07:26.7133942Z Set executable permissions for: /home/runner/.gradle/wrapper/dists/gradle-8.12-bin/cetblhg4pflnnks72fxwobvgv/gradle-8.12/bin/gradle
2025-07-20T15:07:27.2451850Z 
2025-07-20T15:07:27.2455555Z Welcome to Gradle 8.12!
2025-07-20T15:07:27.2456355Z 
2025-07-20T15:07:27.2456839Z Here are the highlights of this release:
2025-07-20T15:07:27.2462991Z  - Enhanced error and warning reporting with the Problems API
2025-07-20T15:07:27.2463768Z  - File-system watching support on Alpine Linux
2025-07-20T15:07:27.2464380Z  - Build and test Swift 6 libraries and apps
2025-07-20T15:07:27.2464694Z 
2025-07-20T15:07:27.2465159Z For more details see https://docs.gradle.org/8.12/release-notes.html
2025-07-20T15:07:27.2465637Z 
2025-07-20T15:07:27.3458956Z Starting a Gradle Daemon (subsequent builds will be faster)
2025-07-20T15:08:26.2446423Z 
2025-07-20T15:08:26.2457673Z > Configure project :dizi
2025-07-20T15:08:26.2458220Z Fetching JAR
2025-07-20T15:08:27.5445150Z e: file:///home/runner/work/TestPlugins/TestPlugins/src/Maciptv/build.gradle.kts:36:5: Unresolved reference: internalName
2025-07-20T15:08:27.5455183Z 
2025-07-20T15:08:27.5459097Z > Configure project :Maciptv
2025-07-20T15:08:27.5463468Z w: file:///home/runner/work/TestPlugins/TestPlugins/src/Maciptv/build.gradle.kts:64:9: 'targetSdk: Int?' is deprecated. Will be removed from library DSL in v9.0. Use testOptions.targetSdk or/and lint.targetSdk instead
2025-07-20T15:08:27.6446747Z 
2025-07-20T15:08:27.6493143Z FAILURE: Build failed with an exception.
2025-07-20T15:08:27.6493510Z 
2025-07-20T15:08:27.6493660Z * Where:
2025-07-20T15:08:27.6494422Z Build file '/home/runner/work/TestPlugins/TestPlugins/src/Maciptv/build.gradle.kts' line: 36
2025-07-20T15:08:27.6495022Z 
2025-07-20T15:08:27.6495208Z * What went wrong:
2025-07-20T15:08:27.6495593Z Script compilation error:
2025-07-20T15:08:27.6495842Z 
2025-07-20T15:08:27.6496240Z   Line 36:     internalName = "Maciptv"                                 
2025-07-20T15:08:27.6496925Z                ^ Unresolved reference: internalName
2025-07-20T15:08:27.6497227Z 
2025-07-20T15:08:27.6497332Z 1 error
2025-07-20T15:08:27.6497482Z 
2025-07-20T15:08:27.6497589Z * Try:
2025-07-20T15:08:27.6498035Z > Run with --stacktrace option to get the stack trace.
2025-07-20T15:08:27.6498682Z > Run with --info or --debug option to get more log output.
2025-07-20T15:08:27.6499237Z > Run with --scan to get full insights.
2025-07-20T15:08:27.6499786Z > Get more help at https://help.gradle.org.
2025-07-20T15:08:27.6500072Z 
2025-07-20T15:08:27.6500238Z BUILD FAILED in 1m 2s
2025-07-20T15:08:28.0718275Z ##[error]Process completed with exit code 1.
2025-07-20T15:08:28.0837443Z Post job cleanup.
2025-07-20T15:08:28.2581696Z Post job cleanup.
2025-07-20T15:08:28.3539802Z [command]/usr/bin/git version
2025-07-20T15:08:28.3576073Z git version 2.50.1
2025-07-20T15:08:28.3618305Z Temporarily overriding HOME='/home/runner/work/_temp/bb17027b-d29d-45b9-bbb3-f9755ada3d26' before making global git config changes
2025-07-20T15:08:28.3619448Z Adding repository directory to the temporary git global config as a safe directory
2025-07-20T15:08:28.3631298Z [command]/usr/bin/git config --global --add safe.directory /home/runner/work/TestPlugins/TestPlugins/builds
2025-07-20T15:08:28.3667653Z [command]/usr/bin/git config --local --name-only --get-regexp core\.sshCommand
2025-07-20T15:08:28.3701358Z [command]/usr/bin/git submodule foreach --recursive sh -c "git config --local --name-only --get-regexp 'core\.sshCommand' && git config --local --unset-all 'core.sshCommand' || :"
2025-07-20T15:08:28.3931923Z [command]/usr/bin/git config --local --name-only --get-regexp http\.https\:\/\/github\.com\/\.extraheader
2025-07-20T15:08:28.3954601Z http.https://github.com/.extraheader
2025-07-20T15:08:28.3967485Z [command]/usr/bin/git config --local --unset-all http.https://github.com/.extraheader
2025-07-20T15:08:28.3999998Z [command]/usr/bin/git submodule foreach --recursive sh -c "git config --local --name-only --get-regexp 'http\.https\:\/\/github\.com\/\.extraheader' && git config --local --unset-all 'http.https://github.com/.extraheader' || :"
2025-07-20T15:08:28.4356314Z Post job cleanup.
2025-07-20T15:08:28.5324998Z [command]/usr/bin/git version
2025-07-20T15:08:28.5365874Z git version 2.50.1
2025-07-20T15:08:28.5410818Z Temporarily overriding HOME='/home/runner/work/_temp/dece203b-230d-429d-80b2-d6a029e68cd6' before making global git config changes
2025-07-20T15:08:28.5412480Z Adding repository directory to the temporary git global config as a safe directory
2025-07-20T15:08:28.5425377Z [command]/usr/bin/git config --global --add safe.directory /home/runner/work/TestPlugins/TestPlugins/src
2025-07-20T15:08:28.5462876Z [command]/usr/bin/git config --local --name-only --get-regexp core\.sshCommand
2025-07-20T15:08:28.5496523Z [command]/usr/bin/git submodule foreach --recursive sh -c "git config --local --name-only --get-regexp 'core\.sshCommand' && git config --local --unset-all 'core.sshCommand' || :"
2025-07-20T15:08:28.5741247Z [command]/usr/bin/git config --local --name-only --get-regexp http\.https\:\/\/github\.com\/\.extraheader
2025-07-20T15:08:28.5767359Z http.https://github.com/.extraheader
2025-07-20T15:08:28.5780307Z [command]/usr/bin/git config --local --unset-all http.https://github.com/.extraheader
2025-07-20T15:08:28.5817588Z [command]/usr/bin/git submodule foreach --recursive sh -c "git config --local --name-only --get-regexp 'http\.https\:\/\/github\.com\/\.extraheader' && git config --local --unset-all 'http.https://github.com/.extraheader' || :"
2025-07-20T15:08:28.6180106Z Cleaning up orphan processes
2025-07-20T15:08:28.6450470Z Terminate orphan process: pid (2469) (java)
