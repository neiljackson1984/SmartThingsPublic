#prepare smartthings-ide-cookie.txt by logging in to smartthing ide in chrome, use developer mode (Ctrl-Shift-C), click the "Save" button when editing deive handler (probably same coookie i submitted with any request), then go toNetwork tab, right-click the request to ide/comile, and select "copy as curl for bash".  Grab the clipboard contents and pull out the string in single quotes after -H that strts with "Cookie: ".  Save this string as smartthings-ide-cookie.txt.
groovyFile=$(firstword $(wildcard *.groovy))
cookieFile=../../../smartthings-ide-cookie.txt
cookiePreparationInstructionalMessage="prepare smartthings-ide-cookie.txt by logging in to smartthing ide in chrome, use developer mode (Ctrl-Shift-C), click the "Save" button when editing deive handler (probably same coookie i submitted with any request), then go toNetwork tab, right-click the request to ide/comile, and select "copy as curl for bash".  Grab the clipboard contents and pull out the string in single quotes after -H that strts with "Cookie:".  Save this string as smartthings-ide-cookie.txt. "
urlOfSmartThings=https://graph-na04-useast2.api.smartthings.com
#set smartThingsId to the id of the device handler or smartApp that you are uploading.
smartThingsId=221620c6-0d27-445a-96cd-5aa61bdc6814

default: ${cookieFile} ${groovyFile}
	curl "${urlOfSmartThings}/ide/device/compile"  \
	    -H '@${cookieFile}'  \
	    --data "id=${smartThingsId}"  \
	    --data "location="  \
	    --data-urlencode "code@${groovyFile}"  \
	    --data "resourceType=script"  \
	    2>nul
	curl "${urlOfSmartThings}/api/smartapps/installations/221cbb88-cd20-4e76-ae95-6867551380cb/runTheTestCode" \
	    -H "Authorization: Bearer 90fd8716-8e4c-441d-8bb0-c13c46b58ca6" \
	    2>nul
		
${cookieFile}: 
	echo ${cookiePreparationInstructionalMessage} > ${cookieFile}
	echo ${cookiePreparationInstructionalMessage}