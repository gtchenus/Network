//
// Created by Guanting Chen on 2019/9/28.
//

#include <iostream>
#include "client.h"


Client::Client(const char *host, const uint32_t port) : hostname(host) {
  if (host == nullptr) {
    std::cout << "Client: undefined hostname, using localhost ..." << std::endl;
    hostname = "localhost";
  }
  std::cout << "Client: initializing a client instance ..." << std::endl;
  std::cout << "Client: collecting host information ..." << std::endl;
  hostent *hp;
  if ((hp = gethostbyname(hostname)) == NULL) {
    throw std::invalid_argument("Client: failed to get host info");
  }
  memset(&client_addr, 0, sizeof(client_addr));
  memcpy((char *)&client_addr.sin_addr, hp->h_addr,hp->h_length); /* set address */
  client_addr.sin_family = hp->h_addrtype;
  client_addr.sin_port = htons(port);
  std::string server_ip;
  std::string server_port;
  parseSocket(&client_addr, server_ip, server_port);
  std::cout <<"Client: "<< "Host " << server_ip << " Port " << server_port << std::endl;
}


int Client::createConnection() {
  std::cout << "Client: creating client socket ..." << std::endl;
  if ((client_sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    perror("Client: failed to create a socket");
    return -1;
  }
  std::cout << "Client: socket starts connecting to host ..." << std::endl;
  if (connect(client_sockfd, (struct sockaddr *)&client_addr, sizeof(client_addr)) < 0) {
    close(client_sockfd);
    perror("Client: socket failed to connect with host ...");
    return -1;
  }
  return 0;
}

int sendAll(int sockfd, const std::string &msg, uint32_t size) {
  uint32_t total_sent = 0;
  uint32_t sent = 0;
  while (total_sent < size) {
    if ((sent = send(sockfd, msg.c_str() + total_sent, size - total_sent, 0)) < 0) {
      return  -1;
    }
    total_sent += sent;
  }
  std::cout << "Client sent message: " << msg.c_str() << std::endl;    // already with '\n'
  return 0;
}

int receiveAll(int sockfd, std::string &msg) {
  char buff[SO_RCVBUF + 1];
  while (msg.find(ENDMSG) == std::string::npos) {
    memset(buff, 0, sizeof(buff));    // must be init first before receiving
    int state = recv(sockfd, buff, SO_RCVBUF, 0);
    if (state < 0) {
      perror("Client: failed to receive the echo message to client, closing socket ...");
      return -1;
    } else if (state == 0) {
      std::cout << "Client: detected server closed, closing socket ..." << std::endl;
      return -1;
    }
    std::string tmp(buff);
    msg.append(tmp);
  }
  std::cout << "Client: received echo: " << msg << std::endl;
  return 0;
}

int main(int argc, char *argv[]) {
  char *host = nullptr;
  uint32_t port = DEFAULTPORT;
  if (argc >= 2) {
    host = argv[1];
    std::cout << "Client: using host: " << host << std::endl;
    if (argc > 2) {
      char *tmp = argv[2];
      port = strtoul(tmp, nullptr, 10);
      if (port >= DEFAULTPORT && port <= 58999) {
        std::cout << "Client: using port: " << port << std::endl;
      } else {
        std::cout << "Client: invalid port number, use number between: 58000 - 58999  "<<std::endl;
        exit(0);
      }
    }
  }
  Client client(host, port);
  // create socket and connect
  if (client.createConnection() < 0) {
    client.closeSocket();
    std::cerr << "Client: terminating ..." << std::endl;
    exit(1);
  }
  std::cout << "Client: connected with host successfully ..." << std::endl;
  std::cout << "Client: specify message you want to send" << std::endl;

  std::string msg;
  // Start sending message
  while (std::getline(std::cin, msg)) {
    // Add ENDMSG for each message
    msg.append(ENDMSG);
    if (msg.find("exit\n")  != std::string::npos ) {
      std::cout << "Client: closing client socket ..." << std::endl;
      client.closeSocket();
      exit(0);
    }
    if (sendAll(client.getSocketFd(), msg, msg.length()) < 0 ) {
      perror("Client: failed to send the message to server, closing socket ...");
      client.closeSocket();
      exit(1);
    }
    msg.clear();
    if (receiveAll(client.getSocketFd(), msg) < 0 ) {
      client.closeSocket();
      exit(1);
    }
    std::cout << "Client: specify message you want to send" << std::endl;
  }

  return 0;
}