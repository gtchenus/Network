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

int Server::handleCSP(int csockfd, const uint32_t &id, CspHandle &csp) {
  std::string csp_msg;
  std::string error_msg = "404 ERROR: Invalid Connection Setup Message\n";
  std::string success_msg = "200 OK: Ready\n";
  if (receiveAll(csockfd, id, csp_msg) < 0 ) {
    std::cerr<< "Server: failed to receive CSP messages from client: "<< id << std::endl;
    return -1;
  }
  // parse
  if (csp_msg.back() != '\n') {
    std::cerr<< "Server: invalid Connection Setup Message from client: "<< id << " with NO ENDMSG" << std::endl;
    if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
      std::cerr<< "Server: failed to send CSP ERROR to client: "<< id << std::endl;
    }
    return -1;
  }
  csp_msg.pop_back();   // remove \n
  const std::vector<std::string> msg_vector = parseMsg(csp_msg, ' ');
  if (msg_vector.size() != 5) {
    std::cerr<< "Server: invalid Connection Setup Message from client: "<< id << " with unmatched format" << std::endl;
    if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
      std::cerr<< "Server: failed to send CSP ERROR to client: "<< id << std::endl;
    }
    return -1;
  }
  csp.protocal_phase = msg_vector[0].at(0);
  csp.measure_type = msg_vector[1];
  csp.prob_nums = std::stoul(msg_vector[2],nullptr,10);
  csp.msg_size = std::stoul(msg_vector[3],nullptr,10);
  csp.server_delay = std::stoul(msg_vector[4],nullptr,10);
  if (csp.protocal_phase != CSP || (csp.measure_type != "rtt" && csp.measure_type != "tput") ||
      csp.prob_nums < 1 || csp.msg_size < 1 || csp.server_delay < 0) {
//    std::cout << "protocal_phase: " << csp.protocal_phase << std::endl;
//    std::cout << "measure_type: " << csp.measure_type << std::endl;
//    std::cout << "prob_nums: " << csp.prob_nums << std::endl;
//    std::cout << "msg_size: " << csp.msg_size << std::endl;
//    std::cout << "server_delay: " << csp.server_delay << std::endl;
    std::cerr<< "Server: invalid Connection Setup Message from client: "<< id << " with invalid parms" << std::endl;
    if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
      std::cerr<< "Server: failed to send CSP ERROR to client: "<< id << std::endl;
    }
    return -1;
  }

  // If reach here, we receive valid CSP message
  // Send success message
  std::cout<< "Server: received Valid Connection Setup Message from client: "<< id << " with " << csp_msg << std::endl;
  std::cout<< "Server: sending CSP success signal to client: "<< id << std::endl;
  if (sendAll(csockfd, id, success_msg, success_msg.length()) < 0) {
    std::cerr<< "Server: failed to send CSP SUCCESS to client: "<< id << std::endl;
    return -1;
  }
  return 0;
}

int Server::handleMP(int csockfd, const uint32_t &id, const CspHandle &csp, MpHandle &mp) {
  std::string mp_msg;
  std::string error_msg = "404 ERROR: Invalid Measurement Message\n";
  std::string success_msg = " (200 OK)\n";
  uint32_t prob_count = 0;    // default, set to same value as client default
  while (prob_count < csp.prob_nums) {
    mp_msg.clear();
//    std::cout << "prob_count: " << prob_count  << std::endl;
    if (receiveAll(csockfd, id, mp_msg) < 0 ) {
      std::cerr<< "Server: failed to receive CSP messages from client: "<< id << std::endl;
      return -1;
    }
    // parse
    if (mp_msg.back() != '\n') {
      std::cerr<< "Server: invalid Measurement Message from client: "<< id << " with NO ENDMSG" << std::endl;
      if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
        std::cerr<< "Server: failed to send MP ERROR to client: "<< id << std::endl;
      }
      return -1;
    }
    // To echo back, no modification on original msg,
    // payload = actual payload + ENDMSG
    //  mp_msg.pop_back();
    const std::vector<std::string> msg_vector = parseMsg(mp_msg, ' ');
    if (msg_vector.size() != 3) {
      std::cerr<< "Server: invalid Connection Setup Message from client: "<< id << " with unmatched format" << std::endl;
      if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
        std::cerr<< "Server: failed to send MP ERROR to client: "<< id << std::endl;
      }
      return -1;
    }
    ++prob_count;
    mp.protocal_phase = msg_vector[0].at(0);
    mp.prob_seq = std::stoul(msg_vector[1],nullptr,10);
    mp.payload = msg_vector[2];

    if (mp.protocal_phase != MP || mp.prob_seq != prob_count || mp.payload.length() - 1 != csp.msg_size) {    // -1 because of ENDMSG
//      std::cout << "protocal_phase: " << mp.protocal_phase << std::endl;
//      std::cout << "prob_seq: " << mp.prob_seq  << std::endl;
//      std::cout << "required prob_seq: " << prob_count  << std::endl;
//      std::cout << "payload: " << mp.payload << std::endl;
//      std::cout << "payload actual size: " << mp.payload.length() - 1 << std::endl;
//      std::cout << "required msg size: " << msg_size<< std::endl;
      std::cerr<< "Server: invalid Measurement Message from client: "<< id << " with invalid parms" << std::endl;
      if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
        std::cerr<< "Server: failed to send MP ERROR to client: "<< id << std::endl;
      }
      return -1;
    }

    std::string valid_msg(mp_msg);
    valid_msg.pop_back();
    valid_msg.append(success_msg);
    // If reach here, we receive a valid MP message
    // Delay for sometime before echoing back
    std::cout<< "Server: received Valid Measurement Setup Message from client: "<< id << std::endl;
    sleep_milliseconds(csp.server_delay);   // delay before echo
    if (sendAll(csockfd, id, valid_msg, valid_msg.length()) < 0) {
      std::cerr << "Server: failed to send MP echo to client: " << id << std::endl;
      return -1;
    }
  }
  // If reach here, we received and echo back #probs valid MP message
  // OK
  return 0;
}

int Server::handleCTP(int csockfd, const uint32_t &id, CtpHandle &ctp) {
  std::string ctp_msg;
  std::string error_msg = "404 ERROR: Invalid Connection Termination Message\n";
  std::string success_msg = "200 OK: Closing Connection\n";
  if (receiveAll(csockfd, id, ctp_msg) < 0 ) {
    std::cerr<< "Server: failed to receive CTP messages from client: "<< id << std::endl;
    return -1;
  }
  // parse
  if (ctp_msg.back() != '\n') {
    std::cerr<< "Server: invalid Connection Termination Message from client: "<< id << " with NO ENDMSG" << std::endl;
    if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
      std::cerr<< "Server: failed to send CTP ERROR to client: "<< id << std::endl;
    }
    return -1;
  }
  ctp_msg.pop_back();   // remove \n
  const std::vector<std::string> msg_vector = parseMsg(ctp_msg, ' ');
  if (msg_vector.size() != 1) {
    std::cerr<< "Server: invalid Connection Termination Message from client: "<< id << " with unmatched format" << std::endl;
    if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
      std::cerr<< "Server: failed to send CTP ERROR to client: "<< id << std::endl;
    }
    return -1;
  }
  ctp.protocal_phase = msg_vector[0].at(0);
  if (ctp.protocal_phase != CTP) {
    std::cerr<< "Server: invalid Connection Termination Message from client: "<< id << " with invalid phase identifier" << std::endl;
    if (sendAll(csockfd, id, error_msg, error_msg.length()) < 0) {
      std::cerr<< "Server: failed to send CTP ERROR to client: "<< id << std::endl;
    }
    return -1;
  }

  // If reach here, we receive valid CTP message
  // Send success message and close connection
  std::cout<< "Server: received Valid Connection Termination Message from client: "<< id << " with " << ctp_msg << std::endl;
  std::cout<< "Server: sending CTP success signal to client: "<< id << std::endl;
  if (sendAll(csockfd, id, success_msg, success_msg.length()) < 0) {
    std::cerr<< "Server: failed to send CTP SUCCESS to client: "<< id << std::endl;
    return -1;
  }
  return 0;
}

int receiveAll(int sockfd, const uint32_t &id, std::string &msg) {
  char buff[SO_RCVBUF + 1];
  while (msg.find(ENDMSG) == std::string::npos) {
    memset(buff, 0, sizeof(buff));    // must be init first before receiving
    int state = recv(sockfd, buff, SO_RCVBUF, 0);
    if (state < 0) {
      perror("Server: failed to receive the echo message from client, closing socket ...");
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
  // CSP phase
  CspHandle csp;
  if (Server::handleCSP(csockfd, id, csp) < 0 ) {
    perror("Server: closing socket ...");
    close(csockfd);
    mtx.lock();
    map->erase(id);
    mtx.unlock();
    std::cout << "Server: thread " << id << " ends" << std::endl;
    return;
  }

  // MP phase
  MpHandle mp;
  if (Server::handleMP(csockfd, id, csp, mp) < 0 ) {
    perror("Server: closing socket ...");
    close(csockfd);
    mtx.lock();
    map->erase(id);
    mtx.unlock();
    std::cout << "Server: thread " << id << " ends" << std::endl;
    return;
  }
  assert(mp.prob_seq == csp.prob_nums);

  // CTP phase
  CtpHandle ctp;
  if (Server::handleCTP(csockfd, id, ctp) == 0 ) {
    std::cout << "Server: Success! All measurements finished with client: " << id << std::endl;
  }
  std::cout << "Server: closing socket with client: " << id << " ..." << std::endl;
  close(csockfd);
  mtx.lock();
  map->erase(id);
  mtx.unlock();
  std::cout << "Server: thread : " << id << " ends" << std::endl;
  return;
//  std::string msg;
//  while (true) {
//    msg.clear();
//    if (receiveAll(csockfd, id, msg) < 0 ) break;
//    if (sendAll(csockfd, id, msg, msg.length()) < 0) break;
//  }
//
//  mtx.lock();
//  map->erase(id);
//  mtx.unlock();
//  std::cout << "Server: thread " << id << " ends" << std::endl;
}

int main(int argc, char *argv[]) {
  uint32_t port = DEFAULTPORT;
  if (argc >= 2) {
    char *tmp = argv[1];
    port = strtoul(tmp, nullptr, 10);
    if (port >= DEFAULTPORT && port <= 58999) {
      std::cout << "Server: using port: " << port << std::endl;
    } else {
      std::cerr << "Server: invalid port number, use number between: 58000 - 58999  "<<std::endl;
      exit(EXIT_FAILURE);
    }
  }
  Server server(port);
  std::map<uint32_t, std::string> clients;
  if (server.establish() < 0 ) {
    std::cerr << "Server: failed to establish a listening socket, terminating ..." << std::endl;
    exit(EXIT_FAILURE);
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
      exit(EXIT_FAILURE);
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