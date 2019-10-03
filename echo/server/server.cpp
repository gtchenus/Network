//
// Created by Guanting Chen on 2019/9/28.
//

#include "server.h"

Server::Server(const uint32_t port) {
  std::cout << "Server: initializing a server instance ..." << std::endl;
  std::cout << "Server: collecting server information ..." << std::endl;
  memset(&server_addr, 0, sizeof(server_addr));
  gethostname(hostname, MAXHOSTNAME);
  std::cout << "host: " << hostname << std::endl;
  hostent *hp;
  if ((hp = gethostbyname(hostname)) == NULL) {
    throw std::invalid_argument("Server: can not get host info");
  }
  server_addr.sin_family = hp->h_addrtype;
  server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
  server_addr.sin_port = htons(port);
  std::string server_ip;
  std::string server_port;
  parseSocket(&server_addr, server_ip, server_port);
  std::cout << "Server: Host " << server_ip << " Port " << server_port << std::endl;
}

int Server::establish() {
  if ((server_sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
    perror("Server: failed to create a socket");
    return -1;
  }
  std::cout << "Server: open a socket successfully ..." << std::endl;
  if (bind(server_sockfd, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
    close(server_sockfd);
    perror("Server: failed to bind address with socket");
    return -1;
  }
  std::cout << "Server: bind address to socket successfully ..." << std::endl;
  if ((listen(server_sockfd, 3)) < 0 ) {
    close(server_sockfd);
    perror("Server: failed to listen the socket");
    return -1;
  }
  std::cout << "Server: waiting for a connection request from clients ..." << std::endl;
  return 0;
}

int receiveAll(int sockfd, const uint32_t &id, std::string &msg) {
  char buff[SO_RCVBUF + 1];
  while (msg.find(ENDMSG) == std::string::npos) {
    memset(buff, 0, sizeof(buff));    // must be init first before receiving
    int state = recv(sockfd, buff, SO_RCVBUF, 0);
    if (state < 0) {
      perror("Server: failed to receive the echo message to client, closing socket ...");
      close(sockfd);
      return -1;
    } else if (state == 0) {
      std::cout << "Server: detected client: " << id << " closed, closing socket ..." << std::endl;
      close(sockfd);
      return -1;
    }
    std::string tmp(buff);
    msg.append(tmp);
  }
  std::cout << "Server received from client " << id << " : " << msg << std::endl;
  return 0;
}

int sendAll(int sockfd, const uint32_t &id, const std::string &msg, uint32_t size) {
  uint32_t total_sent = 0;
  uint32_t sent = 0;
  while (total_sent < size) {
    if ((sent = send(sockfd, msg.c_str() + total_sent, size - total_sent, 0)) < 0) {
      perror("Server: failed to send the echo message to client, closing socket ...");
      close(sockfd);
      return  -1;
    }
    total_sent += sent;
  }
  std::cout << "Server echo to client " << id << " : " << msg << std::endl;
  return 0;
}

void echo(int csockfd, uint32_t id, std::map<uint32_t, std::string> *map) {
  std::string msg;
  while (true) {
    msg.clear();
    if (receiveAll(csockfd, id, msg) < 0 ) break;
    if (sendAll(csockfd, id, msg, msg.length()) < 0) break;
  }
  mtx.lock();
  map->erase(id);
  mtx.unlock();
  std::cout << "Server: thread " << id << " ends" << std::endl;
}

int main(int argc, char *argv[]) {
  uint32_t port = DEFAULTPORT;
  if (argc >= 2) {
    char *tmp = argv[1];
    port = strtoul(tmp, nullptr, 10);
    if (port >= DEFAULTPORT && port <= 58999) {
      std::cout << "Server: using port: " << port << std::endl;
    } else {
      std::cout << "Server: invalid port number, use number between: 58000 - 58999  "<<std::endl;
      exit(0);
    }
  }
  Server server(port);
  std::map<uint32_t, std::string> clients;
  if (server.establish() < 0 ) {
    std::cerr << "Server: failed to establish a listening socket, terminating ..." << std::endl;
    exit(1);
  }
  int connect_fd;
  uint32_t client_id = 0;
  sockaddr_in client_addr;
  socklen_t slen = sizeof(client_addr);

  while (true) {
    std::string client_ip;
    std::string client_port;
    // wait for a connection to occur on a socket
    connect_fd = accept(server.getSocketFd(), (struct sockaddr *)&client_addr, &slen);
    if (connect_fd < 0) {
      server.closeSocket();
      perror("Server: can not accept a connection with client request");
      exit(1);
    }

    parseSocket(&client_addr, client_ip, client_port);
    client_id += 1;
    std::string val = client_ip.append(":").append(client_port);
    mtx.lock();
    clients[client_id] = val;
    mtx.unlock();
    std::cout << "Server: established a connection with client: " << client_ip << " " << client_port <<std::endl;
    std::cout << "Server: starting thread " << client_id << " for client: " << client_ip << std::endl;
    std::thread t1(echo, connect_fd, client_id, &clients);
    t1.detach();
  }
}