# An Multi-threading Cient-Server Echo Application
The is a program that uses the socket interface to send messages based on TCP stream between a pair of processes on different/same machines, namely, a client and a server. The server is supported by multi-threading in terms of dealing with multiple client requests at the same time. For each client, the server will start a worker thread receiving messages from a specified client and echoing the messages back. The thread will not terminate and keep dealing with messages from its client until the client terminate the sockect connection by itself. The messages delivered between server and client regard `'\n'` as their message terminator. Remeber to always start the server first and terminate it manually at last.
 
## Compilation instructions
To compile successfully, you need to have at the version of C++11.

**Client**: use `g++ client.cpp -std=c++1y -o client.out` to compile

**Server**: use `g++ server.cpp -std=c++1y  -lpthread -o server.out` to compile

(You dont necessarily need to compile them again because there are excutable files already provided.)

## Running instructions
1. Always use `./server.out [port](58000)` to run server first! Otherwise the client will never get connected. You can specify any port number from [58000, 58999]. If you don't specify any port number, it will use 58000 in default.
2. After starting your server, use `./client.out [host]("localhost") [port](58000)` to start clients as many as you want. It will use "localhost" in default if you do not specify any host for your starting server. If you want to specify a port number, you must specify the hostname first. The program will use port 58000 in default. You can see the connecting process at both sides.
3. Then you can enter any messages sending to your server and get the same messages echoed back by your server. The sending and receiving process of both server and client can be seen at both sides.
4. If we want to terminate one or some of your clients, enter `exit` at your client side in the same way of sending other messages. The server will detect the termination of your clients and automically reclaim threads corresponding to terminated client. You can see the terminating process at both sides.
5. Finally, to stop the server service, you need to terminate the server manually by using `control + c`.

## Possible tradeoffs & extensions
1. The tradeoff between using `fork()` or worker threads is essentially between using multi-process or multi-threading, which is decided by how complex the task needed to be done and whether parent and child are doing similar tasks. In this application, I use multi-threading on server side to perform a simple task, echoing back the received message for each socket connection with clients. It's a light-weight and different task comparing to main thread which keep building connections with incoming clients.
2. A possible extension of this program so far is to use a worker threadpool, which limits the number of threads that server can use to do echoing tasks. Currently, the program does not limit the number of threads that server can start. If to many clients are connecting and sending messages to server and the same time, server side will consume quite some resources. Besides, if many clients terminate their connections at the same time. Server program may get stuck due to the locking mechanism which updates the connection state of clients.
3. Another extension is that enabling client to be awareful of termination of server.
