//
// Created by Guanting Chen on 2019/9/28.
//

#ifndef PA1_CLIENT_H
#define PA1_CLIENT_H

#include <netdb.h>
#include <stdexcept>
#include <cstdio>
#include <cstring>
#include <iostream>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>

#define DEFAULTPORT 58000
#define ENDMSG "\n"


class Client {
public:
  Client() : Client("localhost", DEFAULTPORT) {
    std::cout << "Client: undefined host name, using localhost with port: 58000" << std::endl;
  }
  Client(const char *host, const uint32_t port);
  int createConnection();
  int getSocketFd() const {return client_sockfd;}
  sockaddr_in getSocketAddress() const {return client_addr;}
  void closeSocket() {close(client_sockfd);}

private:
  int client_sockfd;  // 3: listening socket
  sockaddr_in client_addr;
  const char *hostname;
};

inline void parseSocket(sockaddr_in *socket_addr, std::string &ip, std::string &port) {
  ip = inet_ntoa(socket_addr->sin_addr);
  port = std::to_string(ntohs(socket_addr->sin_port));
}

int sendAll(int sockfd, const std::string &msg, uint32_t size);
int receiveAll(int sockfd, std::string &msg);

#endif //PA1_CLIENT_H
