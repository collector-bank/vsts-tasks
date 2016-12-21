@echo off

set JAVA=%JAVA_HOME%\bin\java

if not "%JAVA_HOME%" == "" goto SET_CLASSPATH

set JAVA=java

echo JAVA_HOME is not set, unexpected results may occur.
echo Set JAVA_HOME to the directory of your local JDK to avoid this message.

:SET_CLASSPATH

rem init classpath
set READY_API_HOME=%~dp0
set OLDDIR=%CD%
cd /d %READY_API_HOME%

set CLASSPATH=%READY_API_HOME%ready-api-ui-1.5.0.jar;%READY_API_HOME%..\lib-common\*;%READY_API_HOME%..\lib-ready\*;%READY_API_HOME%..\.install4j\*
"%JAVA%" -cp "%CLASSPATH%" com.eviware.soapui.tools.JfxrtLocator > %TEMP%\jfxrtpath
set /P JFXRTPATH= < %TEMP%\jfxrtpath
del %TEMP%\jfxrtpath
set CLASSPATH=%CLASSPATH%;%JFXRTPATH%

rem JVM parameters, modify as appropriate
set JAVA_OPTS=-Xms128m -Xmx1000m -XX:MinHeapFreeRatio=20 -XX:MaxHeapFreeRatio=40 -XX:MaxPermSize=256m -Dsoapui.properties=soapui.properties -Dgroovy.source.encoding=iso-8859-1 "-Dsoapui.home=%READY_API_HOME%\" -splash:ready-api-splash.png -Dsun.net.http.allowRestrictedHeaders=true
if "%1" == "debug" set JAVA_OPTS=%JAVA_OPTS% -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8005

if "%READY_API_HOME%" == "" goto START
    set JAVA_OPTS=%JAVA_OPTS% -Dsoapui.ext.libraries="%READY_API_HOME%ext"
    set JAVA_OPTS=%JAVA_OPTS% -Dsoapui.ext.listeners="%READY_API_HOME%listeners"
    set JAVA_OPTS=%JAVA_OPTS% -Dsoapui.ext.actions="%READY_API_HOME%actions"
    set JAVA_OPTS=%JAVA_OPTS% -Djava.library.path="%READY_API_HOME%\"
	set JAVA_OPTS=%JAVA_OPTS% -Dwsi.dir="%READY_API_HOME%..\wsi-test-tools"
rem uncomment to disable browser component
rem    set JAVA_OPTS=%JAVA_OPTS% -Dsoapui.browser.disabled="true"

:START

rem ********* run READY_API ***********
if "%1" == "debug" echo Starting Ready! API in debug mode
"%JAVA%" %JAVA_OPTS% -cp "%CLASSPATH%" com.smartbear.ready.ui.ReadyApiMain %*
cd /d %OLDDIR%
