import socket
import json

# Set up a TCP/IP socket
s = socket.socket(socket.AF_INET,socket.SOCK_STREAM)

# Connect as client to a selected server
# on a specified port
s.connect(("127.0.0.1",2020))

# Protocol exchange - sends and receives



samplejson = '{"jsonrpc":"2.0","method":"geometry"}'
s.send(samplejson)
		
while True:
	resp = s.recv(1024)
	print(resp)

# Close the connection when completed
s.close()
print "\ndone"

