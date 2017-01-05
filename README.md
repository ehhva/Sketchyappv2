# Sketchyappv2
This is a simple android malware/botnet designed to work in the background
on an android device and the associated commandandcontrol server


Requirements
- python3
- android studio
- vlc
	- audio files are in .3gp format, vlc will play these fine
- genymotion emulator
- physical android device
	- for audio recording, the nexus 5 phone worked best


commandandcontrol.py
- usage: commandandcontrol.py (port) - defaults to 80
-Commands
	list- lists the clients that are currently connected to the server
	getlog- gets the clients logs up to that point, then clears logcat
			The log is stored in the client's directory specified by UUID
			Log file format: date-time_applog.log
	getaudio - records audio from devices
			you can manually specify the record time in seconds, otherwise it defaults to 10
			audio file stored in client's directory specified by UUID
			Audio file format: date-time.3gp
	runshell - runs a command on the android device directly and return the output in a file
-Notes
	- each client checks in 30 seconds, so you may have to wait for the clients to check in
	- server marks clients as disconnected if no response for 60 seconds
	- getaudio works best when left to default time
	- each client is given their own directory where commandandcontrol was run to store files
		based on UUID
	

	
SketchyAppv2
- Before running: change SERVER_IP to ip where commandandcontrol.py is running
- You must accept the permissions for the application to connect to the server
- Client set to check in every 30 seconds
- Notes
	- uses a service to communicate with server, runs in background even when application is closed
		to close completely, you must force stop it
	

Bugs/ Known Issues
- if the server is not up when the app attempts to connect, the app may hang
- if server closes unexpectantly, app may hang
- sometimes the client will check in multiple times per second, may be due to not fully completing its task
- client will check in a few times if force closed, probably due to timerTask being executed before closing
- recording audio causes errors using genymotion, use a physical device to test audio recording
- if permissions are not given when requested
- recording audio longer than the checkin time can lead to issues with multiple files sent
- if a client checks in twice before another checks in during a command being run, it may complete without data from a client
- if a client disconnects, the command and control may hang waiting for a file from that client.
- getlog may only get logs pertaining to the app


Incomplete implementations
- Using android's camera without showing a preview is difficult
- extracting arbitrary file not able to work due to permissioning (good in terms of security)


Future Development
- get audio or log for a specific device
- return shell command output not in a file, print directly to stdout on server
- executing sqli commands on android to read and parse SMS and call logs
- executing sqli commands to read the devices contacts
- logic for reconnecting a client gracefully if the connection is severed
- extract an arbitrary file from the filesystem (permissions may prevent this)
