2025-07-18T20:46:25.1094213Z > Task :Pornhub:checkKotlinGradlePluginConfigurationErrors SKIPPED
2025-07-18T20:46:25.1100418Z > Task :Pornhub:preBuild UP-TO-DATE
2025-07-18T20:46:25.1107134Z > Task :Pornhub:preDebugBuild UP-TO-DATE
2025-07-18T20:46:25.1112839Z > Task :Pornhub:generateDebugResValues
2025-07-18T20:46:25.1118925Z > Task :Pornhub:generateDebugResources
2025-07-18T20:46:25.1140324Z > Task :Pornhub:packageDebugResources
2025-07-18T20:46:25.1143332Z > Task :ExampleProvider:parseDebugLocalResources
2025-07-18T20:46:25.1149502Z > Task :dizi:generateDebugRFile
2025-07-18T20:46:25.1150089Z > Task :Pornhub:parseDebugLocalResources
2025-07-18T20:46:25.6094129Z > Task :ExampleProvider:generateDebugRFile
2025-07-18T20:46:25.6095105Z > Task :Pornhub:generateDebugRFile
2025-07-18T20:46:31.5117738Z e: file:///home/runner/work/TestPlugins/TestPlugins/src/Pornhub/src/main/kotlin/com/owencz1998/PornhubProvider.kt:64:13 No parameter with name 'data' found.
2025-07-18T20:46:31.5120900Z e: file:///home/runner/work/TestPlugins/TestPlugins/src/Pornhub/src/main/kotlin/com/owencz1998/PornhubProvider.kt:124:17 No parameter with name 'posterUrl' found.
2025-07-18T20:46:31.5124343Z e: file:///home/runner/work/TestPlugins/TestPlugins/src/Pornhub/src/main/kotlin/com/owencz1998/PornhubProvider.kt:125:17 No parameter with name 'data' found.
2025-07-18T20:46:31.5127029Z 
2025-07-18T20:46:31.5127789Z > Task :Pornhub:compileDebugKotlin FAILED
2025-07-18T20:46:36.4095056Z 
2025-07-18T20:46:36.4095621Z > Task :dizi:compileDebugKotlin
2025-07-18T20:46:36.4098627Z w: file:///home/runner/work/TestPlugins/TestPlugins/src/dizi/src/main/kotlin/com/example/powerDizi.kt:400:17 'constructor(data: String, name: String? = ..., season: Int? = ..., episode: Int? = ..., posterUrl: String? = ..., rating: Int? = ..., description: String? = ..., date: Long? = ...): Episode' is deprecated. Use newEpisode with runTime included.
2025-07-18T20:46:36.4100541Z 
2025-07-18T20:46:36.4101123Z > Task :ExampleProvider:compileDebugKotlin
2025-07-18T20:46:36.5090947Z 
2025-07-18T20:46:36.5096421Z FAILURE: Build failed with an exception.
2025-07-18T20:46:36.5100157Z 
2025-07-18T20:46:36.5109344Z * What went wrong:
2025-07-18T20:46:36.5110258Z Execution failed for task ':Pornhub:compileDebugKotlin'.
2025-07-18T20:46:36.5112609Z > A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
2025-07-18T20:46:36.5114226Z    > Compilation error. See log for more details
2025-07-18T20:46:36.5115459Z 
2025-07-18T20:46:36.5115879Z * Try:
2025-07-18T20:46:36.5117189Z > Run with --stacktrace option to get the stack trace.
2025-07-18T20:46:36.5118271Z > Run with --info or --debug option to get more log output.
2025-07-18T20:46:36.5119108Z > Run with --scan to get full insights.
2025-07-18T20:46:36.5120790Z > Get more help at https://help.gradle.org.
2025-07-18T20:46:36.5121147Z 
2025-07-18T20:46:36.5121345Z BUILD FAILED in 26s
2025-07-18T20:46:36.5125230Z 20 actionable tasks: 20 executed
2025-07-18T20:46:36.9014985Z ##[error]Process completed with exit code 1.
2025-07-18T20:46:36.9092242Z Post job cleanup.
2025-07-18T20:46:37.0816434Z Post job cleanup.
2025-07-18T20:46:37.1756283Z [command]/usr/bin/git version
2025-07-18T20:46:37.1792617Z git version 2.50.1
2025-07-18T20:46:37.1835494Z Temporarily overriding HOME='/home/runner/work/_temp/610fa1cc-9651-4c7a-bfce-5211d6f0a2fe' before making global git config changes
2025-07-18T20:46:37.1836476Z Adding repository directory to the temporary git global config as a safe directory
2025-07-18T20:46:37.1841029Z [command]/usr/bin/git config --global --add safe.directory /home/runner/work/TestPlugins/TestPlugins/builds
2025-07-18T20:46:37.1877497Z [command]/usr/bin/git config --local --name-only --get-regexp core\.sshCommand
2025-07-18T20:46:37.1909698Z [command]/usr/bin/git submodule foreach --recursive sh -c "git config --local --name-only --get-regexp 'core\.sshCommand' && git config --local --unset-all 'core.sshCommand' || :"
2025-07-18T20:46:37.2131933Z [command]/usr/bin/git config --local --name-only --get-regexp http\.https\:\/\/github\.com\/\.extraheader
2025-07-18T20:46:37.2153460Z http.https://github.com/.extraheader
2025-07-18T20:46:37.2166390Z [command]/usr/bin/git config --local --unset-all http.https://github.com/.extraheader
2025-07-18T20:46:37.2197995Z [command]/usr/bin/git submodule foreach --recursive sh -c "git config --local --name-only --get-regexp 'http\.https\:\/\/github\.com\/\.extraheader' && git config --local --unset-all 'http.https://github.com/.extraheader' || :"
2025-07-18T20:46:37.2529126Z Post job cleanup.
2025-07-18T20:46:37.3587868Z [command]/usr/bin/git version
2025-07-18T20:46:37.3629215Z git version 2.50.1
2025-07-18T20:46:37.3674684Z Temporarily overriding HOME='/home/runner/work/_temp/3999d89f-986a-4354-ac7d-cf02df45a12f' before making global git config changes
2025-07-18T20:46:37.3676535Z Adding repository directory to the temporary git global config as a safe directory
2025-07-18T20:46:37.3689707Z [command]/usr/bin/git config --global --add safe.directory /home/runner/work/TestPlugins/TestPlugins/src
2025-07-18T20:46:37.3725251Z [command]/usr/bin/git config --local --name-only --get-regexp core\.sshCommand
2025-07-18T20:46:37.3760133Z [command]/usr/bin/git submodule foreach --recursive sh -c "git config --local --name-only --get-regexp 'core\.sshCommand' && git config --local --unset-all 'core.sshCommand' || :"
2025-07-18T20:46:37.4007671Z [command]/usr/bin/git config --local --name-only --get-regexp http\.https\:\/\/github\.com\/\.extraheader
2025-07-18T20:46:37.4030888Z http.https://github.com/.extraheader
2025-07-18T20:46:37.4044104Z [command]/usr/bin/git config --local --unset-all http.https://github.com/.extraheader
2025-07-18T20:46:37.4078619Z [command]/usr/bin/git submodule foreach --recursive sh -c "git config --local --name-only --get-regexp 'http\.https\:\/\/github\.com\/\.extraheader' && git config --local --unset-all 'http.https://github.com/.extraheader' || :"
2025-07-18T20:46:37.4418186Z Cleaning up orphan processes
2025-07-18T20:46:37.4691933Z Terminate orphan process: pid (2476) (java)
2025-07-18T20:46:37.4721720Z Terminate orphan process: pid (2563) (java)
