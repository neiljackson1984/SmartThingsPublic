/**
 * personal-logger.groovy
 *
 * An app for collecting numerical information (events with a single integer
 * paramater) that has to be manually reported by a human being.  This is useful
 * for logging pain, perhaps or the ingestion of certain things (drugs, food,
 * etc), or the completion of certain actions that would be hard to detect
 * automatically.  This app is particularly designed for cases where the human
 * wants to make a periodic report (for instance, once every n hours, report how
 * many times x happened since the last report). The app will prompt the human
 * (via TTS devices or similar) to make the report, and will become more
 * aggressive with the prompts until the report is given.
 *
 * the criteria for when the app will prompt the human for the report should be
 * flexible and adaptive (not just a prompt at 9:00 am every day , for instance)
 * We want the app to detect when the human has first woken up in the morning
 * (by looking at motion sensor events, perhaps), and issue a prompt shortly
 * after wakeup.
 *
 * This app creates a virtual device that has the dimmer interface. The human
 * logs an event by setting the dim level of the device to something other than
 * zero.  The app will log this event by adding a line to a google sheet (or
 * maybe some more sophisticated web-based log). (TO DO: cache the log entries
 * in order to handle cases where the logging service is unavailable -- save up
 * the log entries until the logging service is available, and notify the user
 * if log entries are lost.
 *
 *
 *
 *
 *
 **/

/** 
 * Interestingly, it seems that (this.class.name ==
 * "com.hubitat.hub.executor.AppExecutor") evaluates to true not only during the
 * normal execution of the app, but also during initialization of the app (the
 * thing that the hubitat does when it first receives an app's code).
 * Presumably, a similar condition would apply when initializing a driver's
 * code.  This could provide a way to have a single groovy file that would be,
 * simultaneously, a valid app and a valid driver.  Such a hack might prove
 * useful for bundling an app and an associated driver into a single file.
 * However, that single file would still have to be inserted into two different
 * places in the Hubitat system, so perhaps there is not much to be gained
 * anyway.
 **/
// if(this.class.name == "com.hubitat.hub.executor.AppExecutor"){}


//to acknowledge input, we will set the level of the input device to NULL_LEVEL.
// If Alexa sees that the value of a dimmer is 55 and then you ask her to set 
// the value to 55, she will not do anything (or, more likely, Alexa herself does something
// but the Alexa Hubitat app doesn't do anything).
// This is another reason why we need to have a level that we regard as "NULL" (i.e. nothing happening).

 

definition(
    name: "Personal Logger",
    namespace: "neiljackson1984",
    author: "Neil Jackson",
    description: "Logs personal events",
    iconUrl: "",
    iconX2Url: "")

mappings {
     path("/runTheTestCode") { action: [GET:"runTheTestCode"] }
}

def mainTestCode(){
	def message = ""

	message += "\n\n";
 
   return message;
}


preferences {
    page(name: "pageOne")
}

def pageOne(){
    def myDate = new Date();
    def myDateFormat = (new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    myDateFormat.setTimeZone(location.timeZone);
    
    dynamicPage(
    	name: "pageOne", 
        title: "Personal Logger", 
        // "allow the app to be installed from this page?" (i.e. "show the OK/Install button?") : yes
        install: true, 
        
        // "allow the app to be uninstalled from this page?" (i.e. "show the "Remove" button?") 
        uninstall: true 
    ) {
    	section() {
            label( 
            	title: "label:", 
                description: "Assign a label for this instance of this app", 
                required: false, 
                defaultValue: "personal-logger --" + myDateFormat.format(myDate)
            );
            input(
                name: "foo", 
                type: "text", 
                title: "this is the title", 
                description: "this is the description",
                // defaultValue: "this is the default value",
                required:false
            
            );
        }



    	section(/*"input"*/) {
            input(
                name: "dimmer", 
                title: "dimmer that this SmartApp will watch:" ,
                type: "capability.switchLevel", 
                description: (getAllChildDevices().isEmpty() ? "NEW CHILD DEVICE" : "CHILD DEVICE: \n" + getAllChildDevices().get(0).toString() ),            
                required:false,
                submitOnChange:true 
                
                // we want to reload the page whenever this preference changes,
                // because we need to give mainPage a chance to either show or
                // not show the deviceName input according to whether we will be
                // creating a child device (which depends on whether the user
                // has selected a device)
            )
            if(!settings.dimmer){ 
                //if there is no selected dimmer input (i.e. if we will be creating and a managing a (virtual) dimmer as a child device)
            	input(
                	name: "preferredLabelForChildDevice", // "labelForNewChildDevice", 
                    title: "Specify a label for the child device", 
                    type:"text",
                    defaultValue: "personal logger " + myDateFormat.format(myDate),
                    required: false
                )
            } 
            
            if(settings.dimmer)
            {
            	paragraph ( "To create a new virtual dimmer as a child device, and use it as the dimmer that this SmartApp will watch, set the above input to be empty.");
            }
            
        }

        section(/*"output"*/) {
            input(
            	title: "speech synthesis devices for notification:",
                name:"speechSynthesizers", 
                type:"capability.speechSynthesis", 
                description: "select any number of speech synthesis devices to be used for notifications and prompts.",
                multiple:true,
                required:false
            )
            input(
            	title: "logging destination url:",
                name:"logDestinationUrl", 
                type:"text", 
                description: "Insert the URL that you generated by following the instructions at https://wp.josh.com/2014/06/04/using-google-spreadsheets-for-logging-sensor-data/",
                required:false
            )

        }
    }
}

String getUniqueIdRelatedToThisInstalledSmartApp(){
    // java.util.regex.Pattern x = new  java.util.regex.Pattern();
    // java.util.regex.Pattern myPattern = java.util.regex.Pattern.compile("(?<=_)([0123456789abcdef]+)(?=@)");
    // def myMatcher= myPattern.matcher((String) this);
    def myMatcher= ((String) this) =~ "(?<=_)([0123456789abcdef]+)(?=@)";
    //myMatcher.find();
    //return myMatcher.group();
    return myMatcher[0][1];
}

//LIFECYCLE FUNCTION
def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

//LIFECYCLE FUNCTION
def uninstalled() {
	log.trace "uninstalling and deleting child devices"
    getAllChildDevices().each {
        log.trace "deleting child device " + it.toString();
       deleteChildDevice(it.deviceNetworkId)
    }
}

//LIFECYCLE FUNCTION
def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	//if dimmer is null (i.e. no existing dimmer switch was selected by the user),
    // then ensure that a child device dimmer exists (create it if needed), and subscribe to its events.
	def deviceNetworkId="virtualDimmerForLogger" + "-" + getUniqueIdRelatedToThisInstalledSmartApp();
	log.debug("deviceNetworkId: " + deviceNetworkId);
    def dimmerToWatch
    if(settings.dimmer)
    {
    	dimmerToWatch = dimmer;
        
        //delete all child devices that might happen to exist
        getAllChildDevices().each {
            //unsubscribe(it);
            if(it.deviceNetworkId != dimmerToWatch.deviceNetworkId) //this guards against the edge case wherein the user has selected the child device of this SmartApp.
           	{
            	deleteChildDevice(it.deviceNetworkId, true);
            }
        }; 
    } else {
        if(getAllChildDevices().isEmpty()){
        	dimmerToWatch = 
                addChildDevice(
                    /*namespace: */           "neiljackson1984",//"smartthings",
                    /*typeName: */            "Personal Logger Child Device",     // How is the SmartThings platform going to decide which device handler to use in the case that I have a custom device handler with the same namespace and name?  Is there any way to specify the device handler's guid here to force the system to use a particular device handler.
                    /*deviceNetworkId: */     deviceNetworkId  , //how can we be sure that our deviceNetworkId is unique?  //should I be generating a guid or similar here.
                    /*hubId: */               settings.theHub?.id,
                    /*properties: */          [
                                                    isComponent: false,
                                                    // name: "", 
                                                    label: settings.preferredLabelForChildDevice, 
                                                    completedSetup: true
                                              ]
                );
            log.debug("just created a child device: " + dimmerToWatch);
        } else {
        	dimmerToWatch = childDevices.get(0);
            //To do: update the properties of dimmerToWatch, if needed, to ensure that the deviceName matches the user's preference
            // (because the user might have changed the value of the device name field.
            if(dimmerToWatch.label != settings.preferredLabelForChildDevice){
                dimmerToWatch.setLabel(settings.preferredLabelForChildDevice);
            }
        }
    }
    
   subscribe(
       dimmerToWatch,
       "level",
       inputHandler
   ) 
   
   if(dimmerToWatch.hasCapability("Switch"))
   {
        //we want to be able to intelligently deal with both the case where dimmer
        //has and the case where dimmer does not have the Switch capability.
       	subscribe(
               dimmerToWatch,
               "switch",
               inputHandler
           ) 
   }

   speak("welcome");
}

def prettyPrint(x){
    return groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(x));
}

def inputHandler(event) {
    // log.debug "inputHandler was called with ${event.name} ${event.value} ${event}"
	// speak("inputHandler was called with ${event.name} ${event.value}")

    log.debug event.getDevice().toString() + ".currentValue(\"level\"): " + event.getDevice().currentValue("level") 
    if(event.name  == "level"){
        int eventValue = event.value as Integer
        if (eventValue != (100)){
            // log.debug("eventValue.class: " + eventValue.class )
            // log.debug("event fields: " +  event.class.getDeclaredFields().collect{it.toString()}.join("\n"))
            // mappifiedEvent = event.class.getDeclaredFields().collectEntries{ [(it.toString()): event.getProperty(it.toString())] }
            
            
            // mappifiedEvent = event.class.getDeclaredFields()
            //     .findAll { !it.synthetic }
            //     .collectEntries { field ->
            //         [field.name, 
            //             "b" //event."$field.name"
            //         ]
            //     };
            // fieldNames = event.class.getDeclaredFields().collectEntries{ [(it.name): it.fieldAccessor]}
            // firstField = event.class.getDeclaredFields().first()
            // log.debug("firstField fields: " + firstField.class.getDeclaredFields().collect{it.toString()}.join("\n")  )
            // // log.debug("fieldNames: " + prettyPrint(fieldNames))
            // log.debug("fieldNames: " + fieldNames)
            // mappifiedEvent = [];

            // log.debug("serialized event: " +  prettyPrint(mappifiedEvent))
            // mappifiedEvent = [
            //         "SOURCE_LOCATION",
            //         "SOURCE_DEVICE",
            //         "SOURCE_APP",
            //         "SOURCE_HUB",
            //         "id",
            //         "archivable",
            //         "data",
            //         "date",
            //         "descriptionText",
            //         "displayed",
            //         "source",
            //         "isStateChange",
            //         "displayName",
            //         "name",
            //         "value",
            //         "unit",
            //         "description",
            //         "translatable",
            //         "type",
            //         "deviceId",
            //         "locationId",
            //         "hubId",
            //         "installedAppId",
            //         "device",
            //         //"location",
            //         "dataString"
            //     ]
            //     .collectEntries { fieldName ->
            //         [fieldName, 
            //             event."$fieldName"
            //         ]
            //     };
            // log.debug("serialized event: " +  prettyPrint(mappifiedEvent))
            // log.debug("serialized event: " +  prettyPrint(['a':1,'b':2]))
            // log.debug("prettyPrint event: " +  prettyPrint(event))
            
            if(settings.logDestinationUrl){
                httpPost(
                    [
                        'uri': settings.logDestinationUrl,
                        'body' : [
                            'date': event.getDate(),
                            'timestamp': event.getUnixTime(),
                            'value': eventValue
                        ],
                        'contentType':groovyx.net.http.ContentType.TEXT,
                        'requestContentType': groovyx.net.http.ContentType.URLENC    
                    ],
                    {response1 ->
                        String response1Text = response1.data.getText();
                        log.debug("response1.contentType: " + response1.contentType);
                        log.debug("response1Text.length(): " + response1Text.length());
                        log.debug("response1Text: " + response1Text);
                    }
                );
            }
            // sleep(1000);
            // speak(event.getDevice().toString() + " " + eventValue);
            speak("log" + " " + eventValue);
            event.getDevice().setLevel((100));
        }
    }
}

def speak(message){
    log.debug("speaking " + ((String) message))
    for (speechSynthesizer in speechSynthesizers){
        speechSynthesizer.speak((String) message);
    }
}

//////////////////////////////////////////////////



def getAppsCodeForLoggingToGoogleSheets(){
//This is the google apps code that is to be pasted into the embedded script of a google sheet
// in order to enable logging (in case the publicly-available template is taken down for some reason)
//see instructions at https://wp.josh.com/2014/06/04/using-google-spreadsheets-for-logging-sensor-data/ 
return '''

// Format a string into text for the HTML response
function out(s) {
    return ContentService.createTextOutput(s).setMimeType(ContentService.MimeType.TEXT);
}

function doPost(e) {
    //  Logger.clear();
    var ssID = ScriptProperties.getProperty('targetSpreadsheetID');
    if (ssID == null) {
        return (out("Property targetSpreadsheetID not found. Be sure to run Setup script."));
    }

    //  Logger.log("Spreadsheet ID=%s", ssID );
    var ss = SpreadsheetApp.openById(ssID);
    if (ss == null) {
        return (out("Could not find spreadsheet ID [" + ssID + "]. Aborting."));
    }

    var sheetName = ScriptProperties.getProperty('targetSheetName');
    if (sheetName == null) {
        return (out("Property targetSheetName not found. Be sure to run Setup script."));
    }

    //  Logger.log( "Target Sheet Name=%s" , sheetName );
    var sheet = ss.getSheetByName(sheetName);
    // No such thing as Spreadsheet.getSheetById()? Really?
    if (sheet == null) {
        return (out("Could not find sheet named  [" + sheetName + "]. Aborting."));
    }

    var parameters = e.parameter; // Grab the  parameters from the request
    var headers;
    if (sheet.getLastColumn() == 0) { // Special case of empty sheet...
        headers = [];
    } else {
        headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0]; //read headers from top row of spreadsheet
    }
    var newHeadersFlag = false;
    var newRow = []; // Hold new row to be Added to bottom
    for (var p in parameters) { // loop through the request parameter names (keys) and put them in the right column in spreadsheet
        //    Logger.log( " parameter key=%s"  ,  p );
        var col = headers.indexOf(p); // Find column for the param name
        if (col < 0) { // if matching col header not found
            // add new column at end
            headers.push(p);
            col = headers.indexOf(p); // Find column for the param name
            newHeadersFlag = true;
            //      Logger.log( "New col=%s" ,  col );
            // Note that it appears if you send multipule params with the smae name that only the first one shows up
        }
        newRow[col] = parameters[p]; // Lookup value of the passed param and put it into the new row we are building
        //    Logger.log( " col=%s value=%s", col  , newRow[col] );
    }

    if (newRow.length == 0) {
        return (out("No parameters found, no data appended.")); // Nessisary becuase appending a blank row causes a Sheet Serivce Error after the script completes
    }

    if (newHeadersFlag) { // We updated some headers, so reflect in the sheet
        var headersRange = [headers]; // Must be 2 dimensional array to set a range
        sheet.getRange(1, 1, 1, headers.length).setValues(headersRange);
    }

    sheet.appendRow(newRow); // Append new row to end of spreadsheet
    return (out("Data appended successfully."));

}

function doGet(e) {
    return (doPost(e));
}

function setupLoggingToCurrentSheet() {
    ScriptProperties.setProperty('targetSpreadsheetID', SpreadsheetApp.getActiveSpreadsheet().getId());
    ScriptProperties.setProperty('targetSheetName', SpreadsheetApp.getActiveSpreadsheet().getActiveSheet().getSheetName());
}

function onOpen() {
    SpreadsheetApp.getActive()
    .addMenu("Setup Logging",
        [{
                name: "Setup Script",
                functionName: "setupLoggingToCurrentSheet"
            }
        ]);
}

'''
}



//==========  WE DO ALL OUR INCLUDES AT THE BOTTOM IN ORDER TO PRESERVE THE MEANINGFULLNESS OF 
// LINE NUMBERS IN WARNING MESSAGES THROWN BY THE HUBITAT (AT LEAST IF THE WARNING MESSAGES ARE COMPLAINING
// ABOUT THINGS HAPPENING IN THE MAIN CODE, ABOVE THIS POINT).

/**

 * If you are including this in an app, you must have the following mapping

 * declared: 

 * mappings {

 *      path("/runTheTestCode") { action: [GET:"runTheTestCode"] }

 * }

 * 

 * 

 * If you are including this in a driver, you must declare the following command:

 * command "runTheTestCode"

 * //Actually, it appears not to be necessary to declare "runTheTestCode" as a command,

 * // but still not a bad idea.

**/


def runTheTestCode(){
    try{
        return respondFromTestCode(mainTestCode());
    } catch (e)
    {
        def debugMessage = ""
        debugMessage += "\n\n" + "================================================" + "\n";
        debugMessage += (new Date()).format("yyyy/MM/dd HH:mm:ss.SSS", location.getTimeZone()) + "\n";
        debugMessage += "encountered an exception: \n${e}\n"
        
        try{
            def stackTraceItems = [];
            
            // in the case where e is a groovy.lang.GroovyRuntimeException, invoking e.getStackTrace() causes a java.lang.SecurityException 
            // (let's call it e1) to be 
            // thrown, saying that 
            // we are not allowed to invoke methods on class groovy.lang.GroovyRuntimeException.
            // The good news is that we can succesfully call e1.getStackTrace(), and the 
            // returned value will contain all the information that we had been hoping to extract from e.getStackTrace().
            // oops -- I made a bad assumption.  It turns out that e1.getStackTrace() does NOT contain the information that we are after.
            // e1.getStackTrace() has the file name and number of the place where e.getStackTrace(), but not of anything before that.
            //So, it looks like we are still out of luck in our attempt to get the stack trace of a groovy.lang.GroovyRuntimeException.

            def stackTrace;
            try{ stackTrace = e.getStackTrace();} catch(java.lang.SecurityException e1) {
                stackTrace = e1.getStackTrace();
            }

            for(item in stackTrace)
            {
                stackTraceItems << item;
            }


            def filteredStackTrace = stackTraceItems.findAll{ it['fileName']?.startsWith("user_") };
			
			//the last element in filteredStackTrace will always be a reference to the line within the runTheTestCode() function body, which
			// isn't too interesting, so we get rid of the last element.
			if(!filteredStackTrace.isEmpty()){
				filteredStackTrace = filteredStackTrace.init();  //The init() method returns all but the last element. (but throws an exception when the iterable is empty.)
			}
            
            // filteredStackTrace.each{debugMessage += it['fileName'] + " @line " + it['lineNumber'] + " (" + it['methodName'] + ")" + "\n";   }
            filteredStackTrace.each{debugMessage += " @line " + it['lineNumber'] + " (" + it['methodName'] + ")" + "\n";   }
                 
        } catch(ee){ 
            debugMessage += "encountered an exception while trying to investigate the stack trace: \n${ee}\n";
            // debugMessage += "ee.getProperties(): " + ee.getProperties() + "\n";
            // debugMessage += "ee.getProperties()['stackTrace']: " + ee.getProperties()['stackTrace'] + "\n";
            debugMessage += "ee.getStackTrace(): " + ee.getStackTrace() + "\n";
            
            
            // // java.lang.Throwable x;
            // // x = (java.lang.Throwable) ee;
            
            // //debugMessage += "x: \n${prettyPrint(x.getProperties())}\n";
            // debugMessage += "ee: \n" + ee.getProperties() + "\n";
            // // debugMessage += "ee: \n" + prettyPrint(["a","b","c"]) + "\n";
            // //debugMessage += "ee: \n${prettyPrint(ee.getProperties())}\n";
        }
        
        // debugMessage += "filtered stack trace: \n" + 
            // groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(filteredStackTrace)) + "\n";
    
        debugMessage += "\n"
        return respondFromTestCode(debugMessage);
    }
}

def respondFromTestCode(message){
    switch(this.class.name){
        case "com.hubitat.hub.executor.AppExecutor":
            return  render( contentType: "text/html", data: message, status: 200);
            break;
        case "com.hubitat.hub.executor.DeviceExecutor": 
            sendEvent( name: 'testEndpointResponse', value: message )
            return null;
            break;
        default: break;
    }
}
