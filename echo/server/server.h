//
// Created by Guanting Chen on 2019/9/28.
//

#ifndef PA1_SERVER_H
#define PA1_SERVER_H

#include <netdb.h>
#include <cstdio>
#include <cstring>
#include <iostream>
#include <climits>
#include <map>
#include <mutex>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <thread>


#define DEFAULTPORT 58000
#define MAXHOSTNAME 255
#define ENDMSG "\n"

class Server {
public:
  Server() : Server(DEFAULTPORT) {
    std::cout << "Server: undefined port number name, using 58000" << std::endl;
  }
  Server(const uint32_t port);
  int establish();
  sockaddr_in getSocketAddress() const { return server_addr; }
  int getSocketFd() const { return server_sockfd; }
  void closeSocket() { close(server_sockfd); }

private:
  int server_sockfd;  // 3: listening socket
  sockaddr_in server_addr;
  char hostname[MAXHOSTNAME + 1];
};

inline void parseSocket(sockaddr_in *socket_addr, std::string &ip, std::string &port) {
  ip = inet_ntoa(socket_addr->sin_addr);
  port = std::to_string(ntohs(socket_addr->sin_port));
}

// Used for multi-thread echo
std::mutex mtx;
void echo(int sockfd, uint32_t id, std::map<uint32_t, std::string> *map);
int sendAll(int sockfd, const uint32_t &id, const std::string &msg, uint32_t size);
int receiveAll(int sockfd, const uint32_t &id, std::string &msg);

#endif //PA1_SERVER_H
