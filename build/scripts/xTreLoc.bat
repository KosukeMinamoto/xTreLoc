@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  xTreLoc startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and X_TRE_LOC_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\xTreLoc-1.0-SNAPSHOT.jar;%APP_HOME%\lib\commons-math3-3.6.1.jar;%APP_HOME%\lib\gt-shapefile-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-tile-client-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-grid-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-swing-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-render-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-coverage-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-cql-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-main-33-SNAPSHOT.jar;%APP_HOME%\lib\jackson-core-2.18.2.jar;%APP_HOME%\lib\jackson-annotations-2.18.2.jar;%APP_HOME%\lib\jackson-databind-2.18.2.jar;%APP_HOME%\lib\jfreechart-1.0.13.jar;%APP_HOME%\lib\TauP-2.6.1.jar;%APP_HOME%\lib\gt-referencing-33-SNAPSHOT.jar;%APP_HOME%\lib\GeographicLib-Java-1.49.jar;%APP_HOME%\lib\gt-http-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-metadata-33-SNAPSHOT.jar;%APP_HOME%\lib\gt-api-33-SNAPSHOT.jar;%APP_HOME%\lib\jcommon-1.0.16.jar;%APP_HOME%\lib\seisFile-2.0.4.jar;%APP_HOME%\lib\slf4j-reload4j-1.7.35.jar;%APP_HOME%\lib\commons-io-2.18.0.jar;%APP_HOME%\lib\imageio-ext-tiff-1.4.14.jar;%APP_HOME%\lib\jt-affine-1.1.30.jar;%APP_HOME%\lib\jt-algebra-1.1.30.jar;%APP_HOME%\lib\jt-bandmerge-1.1.30.jar;%APP_HOME%\lib\jt-bandselect-1.1.30.jar;%APP_HOME%\lib\jt-bandcombine-1.1.30.jar;%APP_HOME%\lib\jt-warp-1.1.30.jar;%APP_HOME%\lib\jt-format-1.1.30.jar;%APP_HOME%\lib\jt-border-1.1.30.jar;%APP_HOME%\lib\jt-buffer-1.1.30.jar;%APP_HOME%\lib\jt-crop-1.1.30.jar;%APP_HOME%\lib\jt-mosaic-1.1.30.jar;%APP_HOME%\lib\jt-lookup-1.1.30.jar;%APP_HOME%\lib\jt-rescale-1.1.30.jar;%APP_HOME%\lib\jt-scale-1.1.30.jar;%APP_HOME%\lib\jt-scale2-1.1.30.jar;%APP_HOME%\lib\jt-zonal-1.1.30.jar;%APP_HOME%\lib\jt-stats-1.1.30.jar;%APP_HOME%\lib\jt-nullop-1.1.30.jar;%APP_HOME%\lib\jt-translate-1.1.30.jar;%APP_HOME%\lib\jt-binarize-1.1.30.jar;%APP_HOME%\lib\jt-colorconvert-1.1.30.jar;%APP_HOME%\lib\jt-errordiffusion-1.1.30.jar;%APP_HOME%\lib\jt-orderdither-1.1.30.jar;%APP_HOME%\lib\jt-colorindexer-1.1.30.jar;%APP_HOME%\lib\jt-imagefunction-1.1.30.jar;%APP_HOME%\lib\jt-classifier-1.1.30.jar;%APP_HOME%\lib\jt-piecewise-1.1.30.jar;%APP_HOME%\lib\jt-rlookup-1.1.30.jar;%APP_HOME%\lib\jt-vectorbin-1.1.30.jar;%APP_HOME%\lib\jt-shadedrelief-1.1.30.jar;%APP_HOME%\lib\jt-utilities-1.1.30.jar;%APP_HOME%\lib\jt-iterators-1.1.30.jar;%APP_HOME%\lib\net.opengis.ows-33-SNAPSHOT.jar;%APP_HOME%\lib\imageio-ext-geocore-1.4.14.jar;%APP_HOME%\lib\imageio-ext-streams-1.4.14.jar;%APP_HOME%\lib\imageio-ext-utilities-1.4.14.jar;%APP_HOME%\lib\org.w3.xlink-33-SNAPSHOT.jar;%APP_HOME%\lib\jai_core-1.1.3.jar;%APP_HOME%\lib\miglayout-3.7-swing.jar;%APP_HOME%\lib\commons-pool-1.5.4.jar;%APP_HOME%\lib\systems-common-2.1.jar;%APP_HOME%\lib\indriya-2.2.jar;%APP_HOME%\lib\si-units-2.1.jar;%APP_HOME%\lib\si-quantity-2.1.jar;%APP_HOME%\lib\uom-lib-common-2.2.jar;%APP_HOME%\lib\unit-api-2.2.jar;%APP_HOME%\lib\jt-zonalstats-1.6.0.jar;%APP_HOME%\lib\jt-utils-1.6.0.jar;%APP_HOME%\lib\jts-core-1.20.0.jar;%APP_HOME%\lib\seedCodec-1.1.1.jar;%APP_HOME%\lib\disruptor-1.2.15.jar;%APP_HOME%\lib\slf4j-api-1.7.35.jar;%APP_HOME%\lib\reload4j-1.2.18.3.jar;%APP_HOME%\lib\commons-text-1.13.0.jar;%APP_HOME%\lib\re2j-1.8.jar;%APP_HOME%\lib\ejml-ddense-0.41.jar;%APP_HOME%\lib\jgridshift-core-1.3.jar;%APP_HOME%\lib\jai_imageio-1.1.jar;%APP_HOME%\lib\jakarta.inject-api-2.0.1.jar;%APP_HOME%\lib\apiguardian-api-1.1.2.jar;%APP_HOME%\lib\commons-lang3-3.17.0.jar;%APP_HOME%\lib\ejml-core-0.41.jar;%APP_HOME%\lib\aircompressor-0.27.jar;%APP_HOME%\lib\jai_codec-1.1.3.jar;%APP_HOME%\lib\bigint-0.7.1.jar;%APP_HOME%\lib\guava-32.0.0-jre.jar;%APP_HOME%\lib\jakarta.annotation-api-1.3.4.jar;%APP_HOME%\lib\org.eclipse.emf.ecore.xmi-2.15.0.jar;%APP_HOME%\lib\org.eclipse.emf.ecore-2.15.0.jar;%APP_HOME%\lib\org.eclipse.emf.common-2.15.0.jar;%APP_HOME%\lib\jaxb-runtime-2.4.0-b180830.0438.jar;%APP_HOME%\lib\jaxb-api-2.4.0-b180830.0359.jar;%APP_HOME%\lib\javax.activation-api-1.2.0.jar;%APP_HOME%\lib\failureaccess-1.0.1.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\checker-qual-3.33.0.jar;%APP_HOME%\lib\error_prone_annotations-2.18.0.jar;%APP_HOME%\lib\j2objc-annotations-2.8.jar;%APP_HOME%\lib\txw2-2.4.0-b180830.0438.jar;%APP_HOME%\lib\istack-commons-runtime-3.0.7.jar;%APP_HOME%\lib\stax-ex-1.8.jar;%APP_HOME%\lib\FastInfoset-1.2.15.jar


@rem Execute xTreLoc
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %X_TRE_LOC_OPTS%  -classpath "%CLASSPATH%" com.treloc.hypotd.App %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable X_TRE_LOC_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%X_TRE_LOC_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
