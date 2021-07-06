/**
 * ==========================  Device Activity Check ==========================
 *  Platform: Hubitat Elevation
 *
 *  Copyright 2021 Scott W. Olsen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  Author: Scott W. Olsen
 */
import groovy.json.JsonSlurper

definition (
	name: "ZM Camera Function Update",
	namespace: "cool.coraltrain",
	author: "Scott W. Olsen",
	description: "Updates specified Zoneminder cameras based on location mode",
	category: "Automation",
	singleInstance: true,
	iconUrl: "https://raw.githubusercontent.com/swolsen/ZM-Camera-Function-Update/cool.coraltrain/surveillance.jpg",
	iconX2Url: "",
	iconX3Url: ""
)

preferences {
	page name: "pageMain"
}

def pageMain() {
	def monitorFunctionsList = ["None", "Monitor", "Modect", "Record", "Mocord", "Nodect"]
	String pTitle = "<img src='https://raw.githubusercontent.com/swolsen/ZM-Camera-Function-Update/cool.coraltrain/surveillance.jpg' height=80 width=80 /> " +
		"<span style='color: #002288; font-size: 2.4em;'>${app.name}</span>"
	dynamicPage(name: "pageMain", title: pTitle, install: true, uninstall: true)	{
		section("<span style='color: #002288; font-size: 1.7em;'>Zoneminder Access Settings</span>") {
			input name: "ZM_Host", type: "string", title: "Host address for ZoneMinder", required: true
			input name: "ZM_userid", type: "string", title: "User ID for ZoneMinder", required: true
			input name: "ZM_pwd", type: "password", title: "User password for ZoneMinder", required: true
			paragraph "<hr style='background-color: #000153; margin: 0.125in; border: 3px solid #000153; border-radius: 4px;' />"
		}
		section("<span style='color: #002288; font-size: 1.7em;'>Monitor Settings</span>") {
			input name: "monitorIDs", type: "string", title: "Enter the monitor IDs to update (Separate with spaces)", required: true
			input name: "modeSelection", type: "mode", title: "Select Modes to use", multiple: true, required: true, submitOnChange: true
      if (modeSelection) {
        // Set a monitor function to use for each mode
        modeSelection.each {
					mode ->
					input name: "monitorFunctionfor${mode}", type: "enum", options: monitorFunctionsList, title: "Select the monitor function to set for ${mode}", required: true
        }
      }
			paragraph "<hr style='background-color: #000153;	margin: 0.125in; border: 3px solid #000153; border-radius: 4px;' />"
    }
		section("<span style='color: #002288; font-size: 1.7em;'>Hubitat Test Settings</span>") {
			input name: "btnTestZMCF", type: "button", title: "Test ZM Camera Funtion"
			input name:	"enableLogging", type: "bool", title: "Enable Debug Logging?", defaultValue: true, required: true

		}
	}
}

def installed() {
	app.updateLabel("Set ZM monitors ${monitorIDs} for location modes ${modeSelection}")
	logDebug("${app.label} > installed()")
	state.ZM_token = null
	//app.removeSetting("monitorFunctionfornull")
	initialize()
	//settings.each { k,v -> logDebug "it.name=$k, it.value=$v" }
}

def updated() {
	logDebug("updated()")
	installed()
}

def initialize() {
	unsubscribe()
	subscribeToEvents()
	logInfo "Initialized"
}

def uninstalled() {
	logDebug("uninstalled()")
	unsubscribe()
}

def subscribeToEvents() {
	logDebug("subscribeToEvents()")
	subscribe(location, "mode", modeChangeHandler)
}

def modeChangeHandler(evt) {
	logDebug("modeChangeHandler(${evt.value})")
	logDebug("Mode: ${location.mode}")
	logDebug("evt.getLocation().name = ${evt.getLocation().name}")
	logInfo("Mode changed to ${evt.value}")
	cameraFunction()
}

def appButtonHandler(btn) {
	if(btn == "btnTestZMCF") {
		cameraFunction()
	}
}

/* =============================================================================
 *
 *
 */
def cameraFunction() {
	try {
		logDebug "Location Mode is: [${location.currentMode}]"
		logDebug("Checking: " + monitorIDs)
		def setThisMontorFunction = settings."monitorFunctionfor${location.currentMode}"
		def Monitors = monitorIDs.split(' ')
		Monitors.each {
			def MonitorFunction = getMonitorFunction(it)
			logInfo "Monitor ${it} is set to ${MonitorFunction}"

			if("${MonitorFunction}" != setThisMontorFunction) {
				logInfo setMonitorFunction(it, setThisMontorFunction)
			}
		}
	}
	catch(Exception e) {
		log.warn(e)
	}
}


/* ================================= getMonitorFunction ============================
 *
 * gets the specified monitor function value
 *
 */
private String getMonitorFunction(String MonitorID) {
	logDebug("getMonitorFunction(${MonitorID})")
	verifyAccessToken() //First check to see if the access token has not expired
	def MonitorFunction
	def ZM_uri = "https://${ZM_Host}/zm/api/monitors/${MonitorID}.json?token=${state.ZM_token.access_token}"
	def requestParams =
		[
			uri: ZM_uri,
			requestContentType: "application/x-www-form-urlencoded;charset=UTF-8",
			textParser: true,
			contentType: 'application/json',
			headers: ['User-Agent':"Hubitat - appID:${app.id} - ${app.label}"],
			ignoreSSLIssues: true,
		]
	try {
		httpGet(requestParams) {
			response ->
			if(response?.status == 200) {
				def jSlurp = new JsonSlurper().parse(response.data)
				//jSlurp.each { logDebug("jSlurp Value: " + it) }
				MonitorFunction = jSlurp.monitor.Monitor.Function
        //logDebug "ZM Monitor ${MonitorID}: " + MonitorFunction
			}
			else {
				log.warn "${response?.status}"
			}
		}
	}
	catch(Exception e) {
		log.warn e
	}
	return MonitorFunction
}

/* ================================= setMonitorFunction ============================
 *
 * sets the specified monitor function to the provided value
 *
 */
private String setMonitorFunction(String MonitorID, String MonitorFunction) {
	verifyAccessToken() //First check to see if the access token has not expired

//	ZM_Connection.setRequestProperty('User-Agent', Agent)
	def jSlurp
	def ZM_uri = "https://${ZM_Host}/zm/api/monitors/${MonitorID}.json?token=${state.ZM_token.access_token}"
	def requestParams =
		[
			uri: ZM_uri,
			requestContentType: "application/x-www-form-urlencoded;charset=UTF-8",
			textParser: true,
			contentType: 'application/json',
			headers: ['User-Agent':"Hubitat - appID:${app.id} - ${app.label}"],
			ignoreSSLIssues: true,
			body: "Monitor[Function]=${MonitorFunction}&Monitor[Enabled]=1"
		]
	try {
		logInfo("Setting monitor ${MonitorID} to ${MonitorFunction}")
		httpPost(requestParams) {
			response ->
			if(response?.status == 200) {
				jSlurp = new JsonSlurper().parse(response.data)
				//jSlurp.each { logDebug("jSlurp Value: " + it) }
			}
			else {
				log.warn "${response?.status}"
			}
		}
	}
	catch(Exception e) {
		log.warn e
	}
	return("Monitor ${MonitorID}: " + jSlurp.message)
}


/* ======================== verifyAccessToken =========================
 *
 * checks to see if access token has expired. If so calls refreshZMAccessToken
 *
 */
private void verifyAccessToken() {
	logDebug("verifyAccessToken()")
	if(state.ZM_token != null) {
		def tm = new Date().getTime()
		long retdiff = (tm - state.ZM_token.token_retrieved) / 1000
		if(retdiff > state.ZM_token.access_token_expires) refreshZMAccessToken()
		logInfo "Token expires " + clockCount(state.ZM_token.access_token_expires - retdiff)
	}
	else refreshZMAccessToken()
}

/* =========================== refreshZMAccessToken ==============================
 *
 * Obtain Access Token for subquent calls
 */
def refreshZMAccessToken() {
	logDebug("refreshZMAccessToken()")
	def ZM_uri = "https://${ZM_Host}/zm/api/host/login.json"
	def ZM_Login = "user=${ZM_userid}&pass=${ZM_pwd}"
	def requestParams =
		[
			uri: ZM_uri,
			requestContentType: "application/x-www-form-urlencoded;charset=UTF-8",
			textParser: true,
			contentType: 'application/json',
			headers: ['User-Agent':"Hubitat - appID:${app.id} - ${app.label}"],
			ignoreSSLIssues: true,
			body: ZM_Login
		]
	try {
		httpPost(requestParams) {
			response ->
			if(response?.status == 200) {
				state.ZM_token = new JsonSlurper().parse(response.data)
				state.ZM_token.remove("version")
				state.ZM_token.remove("apiversion")
				Date tempDate = new Date()
				state.ZM_token.put("token_retrieved", (long)tempDate.getTime())
				//state.ZM_token.each { logDebug(it) }
				logInfo "ZM Token retrieved: " + new Date(state.ZM_token.token_retrieved).format('yyyy-MM-dd HH:mm:ss')
			}
			else {
				log.warn "${response?.status}"
			}
		}
	}
	catch(Exception e) {
		log.warn e
	}
}

def logDebug(msg) {
	if(enableLogging) {
		log.debug "${app.label} > ${msg}"
	}
}

def logInfo(msg) {
	log.info "${app.label} > ${msg}"
}

/*
 * clockCount() returns a formatted clock representation of the long value in seconds provided
 *  .ex 	2 hours, 51 seconds or 1 minute, 22 seconds
 * Author: Scott Olsen
 * 2021-06-23
 */
def clockCount(long seconds) {
	def rtime = ['H':0, 'M':0, 'S':0]
	if(seconds > 3600) {
		rtime.H = (long)(seconds/3600)
		seconds -= rtime.H * 3600
	}
	if(seconds > 60) {
		rtime.M = (long)(seconds/60)
		seconds -= rtime.M * 60
	}
	rtime.S = seconds

	def cv = ""
	if(rtime.H > 0)	{
		cv += rtime.H + ' hour'
		cv += (rtime.H > 1) ? 's, ' : ', '
	}
	if(rtime.M > 0) {
		cv += rtime.M + ' minute'
		cv += (rtime.M > 1) ? 's, ' : ', '
	}
	if(rtime.S > 0) {
		cv += rtime.S + ' second'
		cv += (rtime.S > 1) ? 's ' : ' '
	}
	return cv
}
