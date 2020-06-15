definition(
    name: "Alexa TTS Manager",
    namespace: "ogiewon",
    author: "Dan Ogorchock",
    description: "Manages your Alexa TTS Child Devices",
    iconUrl: "",
    iconX2Url: "")
preferences {
    page(name: "pageOne")
    page(name: "pageTwo")
}
def pageOne(){
    dynamicPage(name: "pageOne", title: "Alexa Cookie and Country selections", nextPage: "pageTwo", uninstall: true) {
        section("Please Enter your alexa.amazon.com 'cookie' file string here (end with a semicolon)") {
            input("alexaCookie", "text", title: "Raw or edited Cookie", submitOnChange: true, required: true)
        }
        if(alexaCookie != null && alexaCookie.contains("Cookie: ")){
            def finalForm
            def preForm = alexaCookie.split("Cookie: ")
            if(preForm.size() > 1) finalForm = preForm[1]?.replace("\"", "") + ";"
            app.updateSetting("alexaCookie",[type:"text", value: finalForm])
        }
        section("Please enter settings for automatic cookie refresh with NodeJS") {
            input("alexaRefreshURL", "text", title: "NodeJS service URL", required: false)
            input("alexaRefreshUsername", "text", title: "NodeJS service Username (not Amazon one)", required: false)
            input("alexaRefreshPassword", "password", title: "NodeJS service Password (not Amazon one)", required: false)
            input("alexaRefreshOptions", "text", title: "Alexa cookie refresh options", required: false, submitOnChange: true)
            input("alexaRefresh", "bool", title: "Force refresh now? (Procedure will require 5 minutes)", submitOnChange: true)
        }
        if(alexaRefreshOptions == null) {
            unschedule()
        }
        else {
            unschedule()
            schedule("0 0 2 1/6 * ? *", refreshCookie)
            if(alexaCookie == null){
                app.updateSetting("alexaCookie",[type:"text", value: getCookieFromOptions(alexaRefreshOptions)])
            }
        }
        if(alexaRefresh) {
            refreshCookie()
            app.updateSetting("alexaRefresh",[type:"bool", value: false])
        }
        section("Please choose your country") {
            input "alexaCountry", "enum", multiple: false, required: true, options: getURLs().keySet().collect()
        }
        section("Notification Device") {
            paragraph "Optionally assign a device for error notifications (like when the cookie is invalid or refresh fails)"
            input "notificationDevice", "capability.notification", multiple: false, required: false
        }
        section("Override Switch") {
            paragraph "Optionally assign a switch that will disable voice when turned off"
            input "overrideSwitch", "capability.switch", multiple: false, required: false
        }
        section("App Name") {
            label title: "Optionally assign a custom name for this app", required: false
        }
    }
}
def pageTwo(){
    dynamicPage(name: "pageTwo", title: "Amazon Alexa Device Selection", install: true, uninstall: true) {
        section("Please select devices to create Alexa TTS child devices for") {
            input "alexaDevices", "enum", multiple: true, required: false, options: getDevices()
        }
        section("") {
            paragraph "<span style='color:red'>Warning!!\nChanging the option below will delete any previously created child devices!!\n"+
                        "Virtual Container driver v1.1.20181118 or higher must be installed on your hub!!</span>"+
                        "<a href='https://github.com/stephack/Hubitat/blob/master/drivers/Virtual%20Container/Virtual%20Container.groovy' target='_blank'> [driver] </a>"+
                        "<a href='https://community.hubitat.com/t/release-virtual-container-driver/4440' target='_blank'> [notes] </a>"
            input "alexaVC", "bool", title: "Add Alexa TTS child devices to a Virtual Container?"
        }
    }
}
def speakMessage(String message, String device) {
    if (overrideSwitch != null && overrideSwitch.currentSwitch == 'off') {
        log.info "${overrideSwitch} is off, AlexaTTS will not speak message '${message}'"
        return
    }
    log.debug "Sending '${message}' to '${device}'"
 sendEvent(name:"speakMessage", value: message, descriptionText: "Sending message to '${device}'")
    if (message == '' || message.length() == 0) {
        log.warn "Message is empty. Skipping sending request to Amazon"
    }
    else {
        atomicState.alexaJSON.devices.any {it->
            if ((it.accountName == device) || (device == "All Echos")) {
                try{
                    def SEQUENCECMD = "Alexa.Speak"
                    def DEVICETYPE = "${it.deviceType}"
                    def DEVICESERIALNUMBER = "${it.serialNumber}"
                    def MEDIAOWNERCUSTOMERID = "${it.deviceOwnerCustomerId}"
                    def LANGUAGE = getURLs()."${alexaCountry}".Language
                    def command = ""
                    if (device == "All Echos") {
                        command = "{\"behaviorId\":\"PREVIEW\",                                    \"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",                                                        \\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",                                                                           \\\"operationPayload\\\":{\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\",                                                                           \\\"expireAfter\\\":\\\"PT5S\\\",                                                                           \\\"content\\\":[{\\\"locale\\\":\\\"${LANGUAGE}\\\",                                                                                             \\\"display\\\":{\\\"title\\\":\\\"AlexaTTS\\\",                                                                                                              \\\"body\\\":\\\"${message}\\\"},                                                                                                              \\\"speak\\\":{\\\"type\\\":\\\"text\\\",                                                                                                                             \\\"value\\\":\\\"${message}\\\"}}],                                                                           \\\"target\\\":{\\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\"}},                                                                           \\\"type\\\":\\\"AlexaAnnouncement\\\"}}\",                                    \"status\":\"ENABLED\"}"
                    }
                    else {
                        command = "{\"behaviorId\":\"PREVIEW\",                                    \"sequenceJson\":\"{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.Sequence\\\",                                                        \\\"startNode\\\":{\\\"@type\\\":\\\"com.amazon.alexa.behaviors.model.OpaquePayloadOperationNode\\\",                                                        \\\"type\\\":\\\"${SEQUENCECMD}\\\",                                                        \\\"operationPayload\\\":{\\\"deviceType\\\":\\\"${DEVICETYPE}\\\",                                                                                  \\\"deviceSerialNumber\\\":\\\"${DEVICESERIALNUMBER}\\\",                                                                                  \\\"locale\\\":\\\"${LANGUAGE}\\\",                                                                                  \\\"customerId\\\":\\\"${MEDIAOWNERCUSTOMERID}\\\",                                                                                  \\\"textToSpeak\\\":\\\"${message}\\\"}}}\",                                    \"status\":\"ENABLED\"}"
                    }
                    def csrf = (alexaCookie =~ "csrf=(.*?);")[0][1]
                    def params = [uri: "https://" + getURLs()."${alexaCountry}".Alexa + "/api/behaviors/preview",
                                  headers: ["Cookie":"""${alexaCookie}""",
                                            "Referer": "https://" + getURLs()."${alexaCountry}".Amazon + "/spa/index.html",
                                            "Origin": "https://" + getURLs()."${alexaCountry}".Amazon,
                                            "csrf": "${csrf}",
                                            "Connection": "keep-alive",
                                            "DNT":"1"],
                                            contentType: "text/plain",
                                            body: command
                                ]
                    httpPost(params) { resp ->
                        if (resp.status != 200) {
                            log.error "'speakMessage()':  httpPost() resp.status = ${resp.status}"
                            notifyIfEnabled("Alexa TTS: Please check your cookie!")
                        }
                    }
                }
               catch (groovyx.net.http.HttpResponseException hre) {
                    if (hre.getResponse().getStatus() != 200) {
                        log.error "'speakMessage()': Error making Call (Data): ${hre.getResponse().getData()}"
                        log.error "'speakMessage()': Error making Call (Status): ${hre.getResponse().getStatus()}"
                        log.error "'speakMessage()': Error making Call (getMessage): ${hre.getMessage()}"
                        if (hre.getResponse().getStatus() == 400) {
                            notifyIfEnabled("Alexa TTS: ${hre.getResponse().getData()}")
                        }
                        else {
                            notifyIfEnabled("Alexa TTS: Please check your cookie!")
                        }
                    }
                }
                catch (e) {
                    log.error "'speakMessage()': error = ${e}"
                    notifyIfEnabled("Alexa TTS: Please check your cookie!")
                }
                return true
            }
        }
    }
}
def getDevices() {
    if (alexaCookie == null) {log.debug "No cookie yet"
                              return}
    try{
        def csrf = (alexaCookie =~ "csrf=(.*?);")[0][1]
        def params = [uri: "https://" + getURLs()."${alexaCountry}".Alexa + "/api/devices-v2/device?cached=false",
                      headers: ["Cookie":"""${alexaCookie}""",
                                "Referer": "https://" + getURLs()."${alexaCountry}".Amazon + "/spa/index.html",
                                "Origin": "https://" + getURLs()."${alexaCountry}".Amazon,
                                "csrf": "${csrf}",
                                "Connection": "keep-alive",
                                "DNT":"1"],
                      requestContentType: "application/json; charset=UTF-8"
                     ]
       httpGet(params) { resp ->
            if ((resp.status == 200) && (resp.contentType == "application/json")) {
                def validDevices = ["All Echos"]
                atomicState.alexaJSON = resp.data
                atomicState.alexaJSON.devices.each {it->
                    if (it.deviceFamily in ["ECHO", "ROOK", "KNIGHT", "THIRD_PARTY_AVS_SONOS_BOOTLEG", "TABLET"]) {
                        validDevices << it.accountName
                    }
                    if (it.deviceFamily == "THIRD_PARTY_AVS_MEDIA_DISPLAY" && it.capabilities.contains("AUDIBLE")) {
                        validDevices << it.accountName
                    }
                }
                log.debug "getDevices(): validDevices = ${validDevices}"
                return validDevices
            }
            else {
                log.error "Encountered an error. http resp.status = '${resp.status}'. http resp.contentType = '${resp.contentType}'. Should be '200' and 'application/json'. Check your cookie string!"
                notifyIfEnabled("Alexa TTS: Please check your cookie!")
                return "error"
            }
        }
    }
    catch (e) {
        log.error "getDevices: error = ${e}"
        notifyIfEnabled("Alexa TTS: Please check your cookie!")
    }
}
private void createChildDevice(String deviceName) {
    log.debug "'createChildDevice()': Creating Child Device '${deviceName}'"
    try {
        def child = addChildDevice("ogiewon", "Child Alexa TTS", "AlexaTTS${app.id}-${deviceName}", null, [name: "AlexaTTS-${deviceName}", label: "AlexaTTS ${deviceName}", completedSetup: true])
    } catch (e) {
           log.error "Child device creation failed with error = ${e}"
    }
}
def installed() {
    log.debug "'Installed()' called with settings: ${settings}"
    updated()
}
def uninstalled() {
    log.debug "'uninstalled()' called"
    childDevices.each { deleteChildDevice(it.deviceNetworkId) }
}
def getURLs() {
    def URLs = ["United States": [Alexa: "pitangui.amazon.com", Amazon: "alexa.amazon.com", Language: "en-US"],
                "Canada": [Alexa: "alexa.amazon.ca", Amazon: "alexa.amazon.ca", Language: "en-US"],
                "United Kingdom": [Alexa: "layla.amazon.co.uk", Amazon: "amazon.co.uk", Language: "en-GB"],
                "Italy": [Alexa: "alexa.amazon.it", Amazon: "alexa.amazon.it", Language: "it-IT"],
                "Australia": [Alexa: "alexa.amazon.com.au", Amazon: "alexa.amazon.com.au", Language: "en-AU"],
                "Brazil": [Alexa: "alexa.amazon.com.br", Amazon: "alexa.amazon.com.br", Language: "pt-BR"]]
    return URLs
}
def updated() {
    log.debug "'updated()' called"
    def devicesToRemove
    if(alexaVC) {
        devicesToRemove = getChildDevices().findAll{it.typeName == "Child Alexa TTS"}
        if(devicesToRemove) purgeNow(devicesToRemove)
        settings.alexaDevices.each {alexaName->
                createContainer(alexaName)
        }
    }
    else {
        devicesToRemove = getChildDevices().findAll{it.typeName == "Virtual Container"}
        if(devicesToRemove) purgeNow(devicesToRemove)
        try {
            settings.alexaDevices.each {alexaName->
                def childDevice = null
                if(childDevices) {
                    childDevices.each {child->
                        if (child.deviceNetworkId == "AlexaTTS${app.id}-${alexaName}") {
                            childDevice = child
                        }
                    }
                }
                if (childDevice == null) {
                    createChildDevice(alexaName)
                    log.debug "Child ${app.label}-${alexaName} has been created"
                }
            }
        }
        catch (e) {
            log.error "Error in updated() routine, error = ${e}"
        }
    }
}
def purgeNow(devices){
    log.debug "Purging: ${devices}"
    devices.each { deleteChildDevice(it.deviceNetworkId) }
}
def createContainer(alexaName){
    def container = getChildDevices().find{it.typeName == "Virtual Container"}
    if(!container){
        log.info "Creating Alexa TTS Virtual Container"
        try {
            container = addChildDevice("stephack", "Virtual Container", "AlexaTTS${app.id}", null, [name: "AlexaTTS-Container", label: "AlexaTTS Container", completedSetup: true])
        } catch (e) {
            log.error "Container device creation failed with error = ${e}"
        }
        createVchild(container, alexaName)
    }
    else {createVchild(container, alexaName)}
}
def createVchild(container, alexaName){
    def vChildren = container.childList()
    if(vChildren.find{it.data.vcId == "${alexaName}"}){
        log.info alexaName + " already exists...skipping"
    }
    else {
        log.info "Creating TTS Device: " + alexaName
        try{
            container.appCreateDevice("AlexaTTS ${alexaName}", "Child Alexa TTS", "ogiewon", "${alexaName}")
        }
        catch (e) {
            log.error "Child device creation failed with error = ${e}"
        }
    }
}
def initialize() {
    log.debug "'initialize()' called"
}
private def getCookieFromOptions(options) {
    try
    {
        def cookie = new groovy.json.JsonSlurper().parseText(options)
        if (!cookie || cookie == "") {
            log.error("'getCookieFromOptions()': wrong options format!")
            notifyIfEnabled("Alexa TTS: Error parsing cookie, see logs for more information!")
            return ""
        }
        cookie = cookie.localCookie.replace('"',"")
        if(cookie.endsWith(",")) {
            cookie = cookie.reverse().drop(1).reverse()
        }
        cookie += ";"
        log.info("Alexa TTS: new cookie parsed succesfully")
        return cookie
    }
    catch(e)
    {
        log.error("'getCookieFromOptions()': error = ${e}")
        notifyIfEnabled("Alexa TTS: Error parsing cookie, see logs for more information!")
        return ""
    }
}
def refreshCookie() {
    log.info("Alexa TTS: starting cookie refresh procedure")
    try {
Map alexaCookieUtility = {
    def proxyServer;
    Map _options = [:];
    String Cookie='';
    final String defaultAmazonPage = 'amazon.de';
    final String defaultUserAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:99.0) Gecko/20100101 Firefox/99.0';
    final String defaultUserAgentLinux = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36';
    final String defaultAcceptLanguage = 'de-DE';
    final List<String> csrfPathCandidates = [
        '/api/language',
        '/spa/index.html',
        '/api/devices-v2/device?cached=false',
        '/templates/oobe/d-device-pick.handlebars',
        '/api/strings'
    ].asImmutable();
    Closure prettyPrint = {
            return groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(it));
    };
    Closure cookie_parse = {String str, Map options=[:] ->
        if (! str instanceof String ) {
            throw new Exception("argument str must be a string");
        }
        if(!str){str='';}
        def pairSplitRegExp = "; *";
        def obj = [:];
        def opt = options + [:];
        def pairs = str.split(pairSplitRegExp);
        for (def i = 0; i < pairs.size(); i++) {
            def pair = pairs[i];
            def eq_idx = pair.indexOf('=');
            if (eq_idx < 0) {
                continue;
            }
            def key = pair.substring(0, eq_idx).trim();
            def val = pair.substring(++eq_idx, pair.length()).trim();
            if ('"' == val[0]) {
                val = val[1..-2];
            }
            if (! obj.containsKey(key)) {
                obj[key] = URLDecoder.decode(val);
            }
        }
        return obj;
    };
    Closure addCookies = {String cookie, headers ->
        String internalDebugMessage = "";
        internalDebugMessage += "addCookies run summary:" + "\n";
        internalDebugMessage += "starting with: " + cookie + "\n";
        String returnValue;
        if (!headers || !headers.any{it.name.toLowerCase() == "set-cookie"} ){
            internalDebugMessage += ("could not find a 'set-cookie' header in headers." + "\n");
            returnValue = cookie;
        } else {
            if(!cookie){
                cookie='';
            }
            def cookies = cookie_parse(cookie);
            for (def headerValue in headers.findAll{it.name.toLowerCase() == "set-cookie"}.collect{it.value}){
                cookieMatch = (~/^([^=]+)=([^;]+);.*/).matcher(headerValue)[0];
                if (cookieMatch && cookieMatch.size() == 3) {
                    if (cookieMatch[1] == 'ap-fid' && cookieMatch[2] == '""'){ continue;}
                    if( (cookieMatch[1] in cookies) && (cookies[cookieMatch[1]] != cookieMatch[2]) ){
                        internalDebugMessage += ('Alexa-Cookie: Update Cookie ' + cookieMatch[1] + ' = ' + cookieMatch[2]) + "\n";
                    } else if (!(cookieMatch[1] in cookies) ) {
                        internalDebugMessage += ('Alexa-Cookie: Add Cookie ' + cookieMatch[1] + ' = ' + cookieMatch[2]) + "\n";
                    } else {
                    }
                    cookies[cookieMatch[1]] = cookieMatch[2];
                }
            }
            returnValue = cookies.collect{it.key + "=" + it.value}.join("; ");
        }
        internalDebugMessage += "addCookies is returning: " + returnValue + "\n";
        _options.logger && _options.logger(internalDebugMessage);
        return returnValue;
    };
    Closure getFields = {String body ->
        Map returnValue = [:];
        body = body.replace("\r", ' ').replace("\n", ' ');
        fieldBlockMatcher = (~/^.*?("hidden"\s*name=".*$)/).matcher(body);
        if (fieldBlockMatcher.find()) {
            fieldMatcher = (~/.*?name="([^"]+)"[\s^\s]*value="([^"]+).*?"/).matcher(fieldBlockMatcher.group(1));
            while (fieldMatcher.find()) {
                if (fieldMatcher.group(1) != 'rememberMe') {
                    returnValue[fieldMatcher.group(1)] = fieldMatcher.group(2);
                }
            }
        }
        return returnValue;
    };
    Closure initConfig = {
        _options.logger = _options.logger ?: Closure.IDENTITY;
        _options.amazonPage = _options.amazonPage ?: _options.formerRegistrationData?.amazonPage ?: defaultAmazonPage;
        _options.logger('Alexa-Cookie: Use as Login-Amazon-URL: ' + _options.amazonPage);
        _options.userAgent = _options.userAgent ?: defaultUserAgentLinux;
        _options.logger('Alexa-Cookie: Use as User-Agent: ' + _options.userAgent);
        _options.acceptLanguage = _options.acceptLanguage ?: defaultAcceptLanguage;
        _options.logger('Alexa-Cookie: Use as Accept-Language: ' + _options.acceptLanguage);
        if (_options.setupProxy && !_options.proxyOwnIp) {
            _options.logger('Alexa-Cookie: Own-IP Setting missing for Proxy. Disabling!');
            _options.setupProxy = false;
        }
        if (_options.setupProxy) {
            _options.setupProxy = true;
            _options.proxyPort = _options.proxyPort ?: 0;
            _options.proxyListenBind = _options.proxyListenBind ?: '0.0.0.0';
            _options.logger('Alexa-Cookie: Proxy-Mode enabled if needed: ' + _options.proxyOwnIp + ':' + _options.proxyPort + ' to listen on ' + _options.proxyListenBind);
        } else {
            _options.setupProxy = false;
            _options.logger('Alexa-Cookie: Proxy mode disabled');
        }
        _options.proxyLogLevel = _options.proxyLogLevel ?: 'warn';
        _options.amazonPageProxyLanguage = _options.amazonPageProxyLanguage ?: 'de_DE';
        if(_options.formerRegistrationData){ _options.proxyOnly = true; }
    };
    Closure getCSRFFromCookies = {Map namedArgs ->
        String cookie = namedArgs.cookie;
        Map options = namedArgs.options ?: [:];
        Closure callback = namedArgs.callback;
        String csrf = null;
        for(csrfPathCandidate in csrfPathCandidates){
            options.logger && options.logger('Alexa-Cookie: Step 4: get CSRF via ' + csrfPathCandidate);
            httpGet(
                [
                    uri: "https://alexa." + options.amazonPage + csrfPathCandidate,
                    'headers': [
                        'DNT': '1',
                        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36',
                        'Connection': 'keep-alive',
                        'Referer': 'https://alexa.' + options.amazonPage + '/spa/index.html',
                        'Cookie': cookie,
                        'Accept': '*/*',
                        'Origin': 'https://alexa.' + options.amazonPage
                    ]
                ],
                {response ->
                    cookie = addCookies(cookie, response.headers);
                    java.util.regex.Matcher csrfMatcher = (~/csrf=([^;]+)/).matcher(cookie);
                    if(csrfMatcher.find()){
                        csrf = csrfMatcher.group(1);
                        options.logger && options.logger('Alexa-Cookie: Result: csrf=' + csrf.toString() + ', Cookie=' + cookie);
                    }
                }
            );
            if(csrf){
                callback && callback(null, [
                    'cookie':cookie,
                    'csrf':csrf
                ]);
                return;
            }
        }
    };
    Closure getLocalCookies = {Map namedArgs ->
        String amazonPage = namedArgs.amazonPage;
        String refreshToken = namedArgs.refreshToken;
        Closure callback = namedArgs.callback;
        Cookie = '';
        Map exchangeParams = [
            'di.os.name': 'iOS',
            'app_version': '2.2.223830.0',
            'domain': '.' + amazonPage,
            'source_token': refreshToken,
            'requested_token_type': 'auth_cookies',
            'source_token_type': 'refresh_token',
            'di.hw.version': 'iPhone',
            'di.sdk.version': '6.10.0',
            'cookies': ('{„cookies“:{".' + amazonPage + '":[]}}').bytes.encodeBase64().toString(),
            'app_name': 'Amazon Alexa',
            'di.os.version': '11.4.1'
        ];
        Map requestParams = [
            uri: 'https://' + 'www.' + amazonPage + '/ap/exchangetoken',
            headers: [
                'User-Agent': 'AmazonWebView/Amazon Alexa/2.2.223830.0/iOS/11.4.1/iPhone',
                'Accept-Language': _options.acceptLanguage,
                'Accept-Charset': 'utf-8',
                'Connection': 'keep-alive',
                'Content-Type': 'application/x-www-form-urlencoded',
                'Accept': '*/*'
            ],
            contentType: groovyx.net.http.ContentType.JSON,
            requestContentType: groovyx.net.http.ContentType.URLENC,
            body: exchangeParams
        ];
        _options.logger('Alexa-Cookie: Exchange tokens for ' + amazonPage);
        _options.logger(prettyPrint(requestParams));
        httpPost(requestParams,
            {response ->
                _options.logger('Exchange Token Response: ' + prettyPrint(response.data));
                if (!response.data.response?.tokens?.cookies) {
                    callback && callback('No cookies in Exchange response', null);
                    return;
                }
                if (!response.data.response.tokens.cookies['.' + amazonPage]) {
                    callback && callback('No cookies for ' + amazonPage + ' in Exchange response', null);
                    return;
                }
                Cookie = addCookies(Cookie, response.headers);
                Map cookies = cookie_parse(Cookie);
                response.data.response.tokens.cookies['.' + amazonPage].each {cookie ->
                    if (cookies[cookie.Name] && cookies[cookie.Name] != cookie.Value) {
                        _options.logger('Alexa-Cookie: Update Cookie ' + cookie.Name + ' = ' + cookie.Value);
                    } else if (!cookies[cookie.Name]) {
                        _options.logger('Alexa-Cookie: Add Cookie ' + cookie.Name + ' = ' + cookie.Value);
                    }
                    cookies[cookie.Name] = cookie.Value;
                };
                String localCookie = cookies.collect{it.key + "=" + it.value}.join("; ");
                callback && callback(null, localCookie);
            }
        );
    };
    Closure handleTokenRegistration = {Map namedArgs ->
        Map options = namedArgs.options ?: [:];
        Map loginData = namedArgs.loginData ?: [:];
        Closure callback = namedArgs.callback;
        options.logger && options.logger('Handle token registration Start: ' + prettyPrint(loginData));
        String deviceSerial;
        if(options.formerRegistrationData?.deviceSerial){
            options.logger && options.logger('Proxy Init: reuse deviceSerial from former data');
            deviceSerial = options.formerRegistrationData.deviceSerial;
        } else {
            Byte[] deviceSerialBuffer = new Byte[16];
            for (def i = 0; i < deviceSerialBuffer.size(); i++) {
                deviceSerialBuffer[i] = floor(random() * 255);
            }
            deviceSerial = deviceSerialBuffer.encodeHex().toString();
        }
        loginData.deviceSerial = deviceSerial;
        Map cookies = cookie_parse(loginData.loginCookie);
        Cookie = loginData.loginCookie;
        Map registerData = [
            "requested_extensions": [
                "device_info",
                "customer_info"
            ],
            "cookies": [
                "website_cookies": cookies.collect{ ["Value": it.value, "Name": it.key] },
                "domain": ".amazon.com"
            ],
            "registration_data": [
                "domain": "Device",
                "app_version": "2.2.223830.0",
                "device_type": "A2IVLV5VM2W81",
                "device_name": "%FIRST_NAME%\u0027s%DUPE_STRATEGY_1ST%ioBroker Alexa2",
                "os_version": "11.4.1",
                "device_serial": deviceSerial,
                "device_model": "iPhone",
                "app_name": "ioBroker Alexa2",
                "software_version": "1"
            ],
            "auth_data": [
                "access_token": loginData.accessToken
            ],
            "user_context_map": [
                "frc": cookies.frc
            ],
            "requested_token_type": [
                "bearer",
                "mac_dms",
                "website_cookies"
            ]
        ];
        Map requestParams0 = [
            uri: "https://api.amazon.com/auth/register",
            headers: [
                'User-Agent': 'AmazonWebView/Amazon Alexa/2.2.223830.0/iOS/11.4.1/iPhone',
                'Accept-Language': options.acceptLanguage,
                'Accept-Charset': 'utf-8',
                'Connection': 'keep-alive',
                'Content-Type': 'application/json',
                'Cookie': loginData.loginCookie,
                'Accept': '*/*',
                'x-amzn-identity-auth-domain': 'api.amazon.com'
            ],
            contentType: groovyx.net.http.ContentType.JSON,
            requestContentType: groovyx.net.http.ContentType.JSON,
            body: registerData
        ];
        options.logger && options.logger('Alexa-Cookie: Register App');
        options.logger && options.logger(prettyPrint(requestParams0));
        httpPost(requestParams0,
            {response0 ->
                options.logger && options.logger('Register App Response: ' + prettyPrint(response0.data));
                if(! response0.data.response?.success?.tokens?.bearer){
                    callback && callback('No tokens in Register response', null);
                    return;
                }
                Cookie = addCookies(Cookie, response0.headers);
                loginData.refreshToken = response0.data.response.success.tokens.bearer.refresh_token;
                loginData.tokenDate = now();
                Map requestParams1 = [
                    uri: "https://alexa.amazon.com/api/users/me?platform=ios&version=2.2.223830.0",
                    headers: [
                        'User-Agent': 'AmazonWebView/Amazon Alexa/2.2.223830.0/iOS/11.4.1/iPhone',
                        'Accept-Language': options.acceptLanguage,
                        'Accept-Charset': 'utf-8',
                        'Connection': 'keep-alive',
                        'Accept': 'application/json',
                        'Cookie': Cookie
                    ],
                    contentType: groovyx.net.http.ContentType.JSON,
                ];
                options.logger && options.logger('Alexa-Cookie: Get User data');
                options.logger && options.logger(prettyPrint(requestParams1));
                httpGet(requestParams1,
                    {response1 ->
                        options.logger && options.logger('Get User data Response: ' + prettyPrint(response1.data));
                        Cookie = addCookies(Cookie, response1.headers);
                        if (response1.data.marketPlaceDomainName) {
                            java.util.regex.Matcher amazonPageMatcher = (~/^[^\.]*\.([\S\s]*)$/).matcher(response1.data.marketPlaceDomainName);
                            if(amazonPageMatcher.find()){
                                options.amazonPage = amazonPageMatcher.group(1);
                            }
                        }
                        loginData.amazonPage = options.amazonPage;
                        loginData.loginCookie = Cookie;
                        getLocalCookies(
                            amazonPage: loginData.amazonPage,
                            refreshToken: loginData.refreshToken,
                            callback: {String err0, String localCookie ->
                                if (err0) {
                                    callback && callback(err0, null);
                                }
                                loginData.localCookie = localCookie;
                                getCSRFFromCookies(
                                    cookie: loginData.localCookie,
                                    options: options,
                                    callback: {String err1, Map resData ->
                                        if (err1) {
                                            callback && callback('Error getting csrf for ' + loginData.amazonPage + ':' + err1, null);
                                            return;
                                        }
                                        loginData.localCookie = resData.cookie;
                                        loginData.csrf = resData.csrf;
                                        loginData.remove('accessToken');
                                        options.logger && options.logger('Final Registraton Result: ' + prettyPrint(loginData));
                                        callback && callback(null, loginData);
                                    }
                                );
                            }
                        );
                    }
                );
            }
        );
    };
    Closure generateAlexaCookie = {Map namedArgs ->
        String email = namedArgs.email;
        String password = namedArgs.password;
        Map __options = namedArgs.options ?: [:];
        Closure callback = namedArgs.callback;
        if (!email || !password) {__options.proxyOnly = true;}
        _options = __options;
        initConfig();
        if(_options.proxyOnly){
        } else {
            _options.logger('Alexa-Cookie: Step 1: get first cookie and authentication redirect');
            Map requestParams0 = [
                'uri': "https://" + 'alexa.' + _options.amazonPage,
                'headers': [
                    'DNT': '1',
                    'Upgrade-Insecure-Requests': '1',
                    'User-Agent': _options.userAgent,
                    'Accept-Language': _options.acceptLanguage,
                    'Connection': 'keep-alive',
                    'Accept': '*/*'
                ],
                'contentType':groovyx.net.http.ContentType.TEXT
            ];
            _options.logger("working on step 1, we will now attempt to get " + requestParams0.uri);
            httpGet(requestParams0,
                {response0 ->
                    _options.logger('First response has been received.');
                    _options.logger("\n" + "response0.headers: " + "\n" + response0.headers.collect{"\t"*1 + it.name + ": " + it.value}.join("\n\n") + "\n");
                    _options.logger('Alexa-Cookie: Step 2: login empty to generate session');
                    Cookie = addCookies(Cookie, response0.headers);
                    String response0Text = response0.data.getText();
                    _options.logger("Cookie: " + Cookie);
                    _options.logger("cookie_parse(Cookie): " + prettyPrint(cookie_parse(Cookie)));
                    _options.logger("\n" + "response0.getContext()['http.request'].getRequestLine().getUri(): " + response0.getContext()['http.request'].getRequestLine().getUri());
                    _options.logger("\n" + "response0.getContext()['http.request'].getOriginal().getRequestLine().getUri(): " + response0.getContext()['http.request'].getOriginal().getRequestLine().getUri() + "\n");
                    _options.logger("response0.contentType: " + response0.contentType);
                    _options.logger("response0Text.length(): " + response0Text.length());
                    Map response0Fields = getFields(response0Text);
                    _options.logger('response0Fields: ' + prettyPrint(response0Fields) + "\n\n" )
                    requestParams1 = requestParams0 + [
                        'uri': "https://" + 'www.' + _options.amazonPage + '/ap/signin',
                        'body': response0Fields,
                        'requestContentType': groovyx.net.http.ContentType.URLENC
                    ];
                    requestParams1.headers += [
                        'Cookie': Cookie,
                        'Referer': 'https://' + {it.host + it.path}(new java.net.URI(response0.getContext()['http.request'].getOriginal().getRequestLine().getUri())),
                        'Content-Type': 'application/x-www-form-urlencoded'
                    ];
                    _options.logger("working on step 2, we will now attempt to post to " + requestParams1.uri + " the following " + prettyPrint(requestParams1.body));
                    httpPost(requestParams1,
                        {response1 ->
                            _options.logger('Second response has been received.');
                            Cookie = addCookies(Cookie, response1.headers);
                            String response1Text = response1.data.getText();
                            _options.logger("Cookie: " + Cookie);
                            _options.logger("cookie_parse(Cookie): " + prettyPrint(cookie_parse(Cookie)));
                            _options.logger("response1.contentType: " + response1.contentType);
                            _options.logger("response1Text.length(): " + response1Text.length());
                            Map response1Fields = getFields(response1Text);
                            _options.logger('response1Fields: ' + prettyPrint(response1Fields) + "\n\n" );
                            _options.logger('Alexa-Cookie: Step 3: login with filled form, referer contains session id');
                            requestParams2 = requestParams1 + [
                                'body':
                                    response1Fields + [
                                        'email': email ?: '',
                                        'password': password ?: ''
                                    ]
                            ];
                            requestParams2.headers += [
                                'Cookie': Cookie,
                                'Referer': "https://www.${_options.amazonPage}/ap/signin/" + (~/session-id=([^;]+)/).matcher(Cookie)[0][1]
                            ];
                            _options.logger("working on step 3, we will now attempt to post to " + requestParams2.uri + " the following: " + prettyPrint(requestParams2.body));
                            httpPost(requestParams2,
                                {response2 ->
                                    _options.logger('Third response has been received.');
                                    String response2Text = response2.data.getText();
                                    _options.logger("response2.contentType: " + response2.contentType);
                                    _options.logger("response2Text.length(): " + response2Text.length());
                                    if({it.host.startsWith('alexa') && it.path.endsWith('.html')}(new java.net.URI(response0.getContext()['http.request'].getOriginal().getRequestLine().getUri())) ){
                                        return getCSRFFromCookies('cookie':Cookie, 'options':_options, 'callback':callback);
                                    } else {
                                        String errMessage = 'Login unsuccessfull. Please check credentials.';
                                        java.util.regex.Matcher amazonMessageMatcher = (~/auth-warning-message-box[\S\s]*"a-alert-heading">([^<]*)[\S\s]*<li><[^>]*>\s*([^<\n]*)\s*</).matcher(response2Text);
                                        if(amazonMessageMatcher.find()){
                                            errMessage = "Amazon-Login-Error: ${amazonMessageMatcher.group(1)}: ${amazonMessageMatcher.group(2)}";
                                        }
                                        if (_options.setupProxy) {
                                        }
                                        callback && callback(errMessage, null);
                                        return;
                                    }
                                }
                            );
                        }
                    );
                }
            );
        }
    };
    Closure refreshAlexaCookie = {Map namedArgs ->
        Map options = namedArgs.options ?: [:];
        Closure callback = namedArgs.callback;
        if(!(options.formerRegistrationData?.loginCookie && options.formerRegistrationData?.refreshToken )){
            callback && callback('No former registration data provided for Cookie Refresh', null);
            return;
        }
        _options = options;
        _options.proxyOnly = true;
        initConfig();
        Map refreshData = [
            "app_name": "ioBroker Alexa2",
            "app_version": "2.2.223830.0",
            "di.sdk.version": "6.10.0",
            "source_token": _options.formerRegistrationData.refreshToken,
            "package_name": "com.amazon.echo",
            "di.hw.version": "iPhone",
            "platform": "iOS",
            "requested_token_type": "access_token",
            "source_token_type": "refresh_token",
            "di.os.name": "iOS",
            "di.os.version": "11.4.1",
            "current_version": "6.10.0"
        ];
        Cookie = _options.formerRegistrationData.loginCookie;
        Map requestParams = [
            uri: "https://api.amazon.com/auth/token",
            headers: [
                'User-Agent': 'AmazonWebView/Amazon Alexa/2.2.223830.0/iOS/11.4.1/iPhone',
                'Accept-Language': _options.acceptLanguage,
                'Accept-Charset': 'utf-8',
                'Connection': 'keep-alive',
                'Content-Type': 'application/x-www-form-urlencoded',
                'Cookie': Cookie,
                'Accept': 'application/json',
                'x-amzn-identity-auth-domain': 'api.amazon.com'
            ],
            contentType: groovyx.net.http.ContentType.JSON,
            requestContentType: groovyx.net.http.ContentType.URLENC,
            body: refreshData
        ];
        _options.logger('Alexa-Cookie: Refresh Token');
        _options.logger(prettyPrint(requestParams));
        httpPost(requestParams,
            {response ->
                _options.logger('Refresh Token Response: ' + prettyPrint(response.data));
                _options.formerRegistrationData.loginCookie = addCookies(_options.formerRegistrationData.loginCookie, response.headers);
                if (!response.data.access_token) {
                    callback && callback('No new access token in Refresh Token response', null);
                    return;
                }
                _options.formerRegistrationData.accessToken = response.data.access_token;
                getLocalCookies(
                    amazonPage: 'amazon.com',
                    refreshToken: _options.formerRegistrationData.refreshToken,
                    callback: {String err, String comCookie ->
                        if (err) {
                            callback && callback(err, null);
                        }
                        _options.logger("_options.formerRegistrationData.loginCookie: " + _options.formerRegistrationData.loginCookie + "\n");
                        Map initCookies = cookie_parse(_options.formerRegistrationData.loginCookie);
                        _options.logger("initCookies: " + "\n" + prettyPrint(initCookies) + "\n\n");
                        String newCookie = 'frc=' + initCookies.frc + '; ';
                        newCookie += 'map-md=' + initCookies['map-md'] + '; ';
                        newCookie += comCookie ?: '';
                        _options.logger("newCookie: " + newCookie + "\n");
                        _options.formerRegistrationData.loginCookie = newCookie;
                        handleTokenRegistration(
                            options: _options,
                            loginData: _options.formerRegistrationData,
                            callback: callback
                        );
                    }
                );
            }
        );
    };
    return [
        'refreshAlexaCookie': refreshAlexaCookie,
        'generateAlexaCookie': generateAlexaCookie,
    ].asImmutable();
}();
        alexaCookieUtility.refreshAlexaCookie(
            options: [
                logger: {log.debug("refreshCookie: " + it + "\n");},
                formerRegistrationData: (new groovy.json.JsonSlurper()).parseText(alexaRefreshOptions)
            ],
            callback: {String error, Map result ->
                if(error){
                    log.debug("error string that resulted from attempting to refresh the alexa cookie: " + error + "\n");
                    notifyIfEnabled("Alexa TTS: Error refreshing cookie, see logs for more information!");
                } else if(!result){
                    log.debug("alexaCookieUtility.refreshAlexaCookie did not return an explicit error, but returned a null result." + "\n");
                    notifyIfEnabled("Alexa TTS: Error refreshing cookie, see logs for more information!");
                } else {
                    log.debug("alexaCookieUtility.refreshAlexaCookie returned the following succesfull result: " + "\n" + groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(result)) + "\n");
                    def newOptions = new groovy.json.JsonBuilder(result).toString()
                    app.updateSetting("alexaRefreshOptions",[type:"text", value: newOptions])
                    log.info("Alexa TTS: cookie downloaded succesfully")
                    app.updateSetting("alexaCookie",[type:"text", value: getCookieFromOptions(newOptions)])
                    sendEvent(name:"GetCookie", descriptionText: "New cookie downloaded succesfully")
                }
            }
        );
    }
    catch (groovyx.net.http.HttpResponseException hre) {
        if (hre.getResponse().getStatus() != 200) {
            log.error "'refreshCookie()': Error making Call (Data): ${hre.getResponse().getData()}"
            log.error "'refreshCookie()': Error making Call (Status): ${hre.getResponse().getStatus()}"
            log.error "'refreshCookie()': Error making Call (getMessage): ${hre.getMessage()}"
            if (hre.getResponse().getStatus() == 400) {
                notifyIfEnabled("Alexa TTS: ${hre.getResponse().getData()}")
            }
            else {
                notifyIfEnabled("Alexa TTS: Error sending request for cookie refresh, see logs for more information!")
            }
        }
    }
    catch (e) {
        log.error "'refreshCookie()': error = ${e}"
        notifyIfEnabled("Alexa TTS: Error sending request for cookie refresh, see logs for more information!")
    }
}
def notifyIfEnabled(message) {
    if (notificationDevice) {
        notificationDevice.deviceNotification(message)
    }
}
