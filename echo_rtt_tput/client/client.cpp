//
// Created by Guanting Chen on 2019/9/28.
//

#include <iostream>
#include "client.h"


Client::Client(const char *host, const uint32_t port) : hostname(host), phase(START) {
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
int Client::issueCSP(const CspMsg &msg) {
  assert(msg.protocal_phase == CSP);
  assert(msg.measure_type == "rtt" || msg.measure_type == "tput");
  assert(msg.prob_nums >= 0);
  assert(msg.msg_size >= 0);
  assert(msg.server_delay >= 0);
  std::cout << "Client starts issuing csp message: " << std::endl;
  const std::string csp_msg = msg.toString();
  if (sendAll(this->getSocketFd(), csp_msg, csp_msg.length()) < 0 ) {
    perror("Client: failed to issue CSP message to server");
    return -1;
  }
  std::string rec;
  if (receiveAll(this->getSocketFd(), rec) < 0) {
    perror("Client: failed to receive CSP feedback from server");
    return -1;
  }
  std::cout << "Client received csp feedback: " << rec << std::endl;
  if (rec.find("200 OK: Ready") != std::string::npos) {
    return 1;
  } else if (rec.find("404 ERROR: Invalid Connection Setup Message") != std::string::npos) {
    return 0;
  } else {
    perror("Client: received unrecognized CSP feedback from server ...");
    return  -1;
  }
}

int Client::startMP(MpMsg &msg, const uint32_t probs) {
  assert(msg.protocal_phase == MP);
  assert(msg.payload.length() == msg.size_);
  assert(msg.prob_seq == 0);
  std::string mp_msg = msg.toString();
  while (msg.prob_seq < probs) {
    msg.prob_seq += 1;
    mp_msg = msg.toString();
    std::cout << "Client starts issuing "<< msg.prob_seq << " of " << probs <<" mp message: " << std::endl;
    if (sendAll(this->getSocketFd(), mp_msg, mp_msg.length()) < 0 ) {
      perror("Client: failed to issue MP message to server, closing socket ...");
      return -1;
    }
    std::string rec;
    if (receiveAll(this->getSocketFd(), rec) < 0) {
      perror("Client: failed to receive MP feedback from server, closing socket ...");
      return -1;
    }
    std::cout << "Client received MP feedback: " << rec <<std::endl;
    if (rec.find("200 OK") != std::string::npos) {
      continue;
    } else if (rec.find("404 ERROR: Invalid Measurement Message") != std::string::npos) {
      return 0;
    } else {
      perror("Client: received unrecognized MP feedback from server ...");
      return  -1;
    }
  }
  return 1;   // OK
}

int Client::issueCTP() {
  CtpMsg ctp;
  assert(ctp.protocal_phase == CTP);
  const std::string ctp_msg = ctp.toString();
  if (sendAll(this->getSocketFd(), ctp_msg, ctp_msg.length()) < 0 ) {
    perror("Client: failed to issue CTP message to server, closing socket ...");
    return -1;
  }
  std::string rec;
  if (receiveAll(this->getSocketFd(), rec) < 0) {
    perror("Client: failed to receive CTP feedback from server, closing socket ...");
    return -1;
  }
  std::cout << "Client received CTP feedback: " << rec << std::endl;
  if (rec.find("200 OK: Closing Connection") != std::string::npos) {
    return 1;
  } else if (rec.find("404 ERROR: Invalid Connection Termination Message") != std::string::npos) {
    perror("Client: received invalid CTP feedback from server ...");
    return 0;
  } else {
    perror("Client: received unrecognized CTP feedback from server ...");
    return  -1;
  }
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
  std::cout << "Client: received msg: " << msg << std::endl;
  return 0;
}

inline int parseProtocalCmd(std::string &type, uint32_t &probs,
                             uint32_t &size, uint32_t &delay) {
  while (true) {
    std::cout << "Client: specify every configuration you want to measure, including\n"
              << "e.g. TYPE(rtt or tput) | #PROBS(10) | MESSAGE_SIZE(100) | SERVER_DELAY(0)\n"
              << "Please input each configuration in sequence and with whitespace between each of them\n" << std::endl;
    std::cin >> type >> probs >> size >> delay;
    if ((type != "rtt" && type != "tput") || probs < 1 || size < 1 || delay < 0){
      std::cerr << "Client: invalid configuration, Type must be rtt or tput, #PROBS must > 0, MESSAGE_SIZE must > 0 "
                << "DELAY must >= 0" << std::endl;
      return -1;
    } else {
      std::cout << "Client: Success! using TYPE(" << type << ") | #PROBS(" << probs << ") | MESSAGE_SIZE("
                << size << ") | SERVER_DELAY("<< delay <<"milliseconds) " <<std::endl;
      break;
    }
  }
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
  assert(client.getPhase() == START);
  // create socket and connect
  if (client.createConnection() < 0) {
    client.closeSocket();
    std::cerr << "Client: terminating ..." << std::endl;
    exit(1);
  }
  std::cout << "Client: connected with host successfully ..." << std::endl;

  // Phase pre-CSP, set up protocal from cmd
  client.setPhase(CSP);
  std::string type;         // "rtt" or "tput"
  uint32_t prob_num = 0;    // > 0
  uint32_t msg_size = 0;    // > 0
  uint32_t delay = 0;       // >= 0
  if (parseProtocalCmd(type, prob_num, msg_size, delay) < 0) {
    std::cout << "Client: closing socket ..." << std::endl;
    client.closeSocket();
    exit(EXIT_FAILURE);
  }
  // Phase CSP
  CspMsg csp(type, prob_num, msg_size, delay);
  if (client.issueCSP(csp) <= 0 ) {
    std::cout << "Client: closing socket ..." << std::endl;
    client.closeSocket();
    exit(EXIT_FAILURE);
  }
  // Phase MP, measure RTT and throughput at this phase
  client.setPhase(MP);
  struct timeval begin, end;
  gettimeofday(&begin, NULL);   // begin timestamp
  MpMsg mp(0, csp.msg_size);
  if (client.startMP(mp, csp.prob_nums) <= 0 ) {
    std::cout << "Client: closing socket ..." << std::endl;
    client.closeSocket();
    exit(EXIT_FAILURE);
  }
  gettimeofday(&end, NULL);   // end timestamp
  double elapsed = (end.tv_sec - begin.tv_sec) +
                   ((end.tv_usec - begin.tv_usec)/1000000.0);
  // Phase CTP
  assert(mp.prob_seq == csp.prob_nums);
  client.setPhase(CTP);
  if (client.issueCTP() <= 0 ) {
    std::cout << "Client: closing socket ..." << std::endl;
    client.closeSocket();
    exit(EXIT_FAILURE);
  }
  std::cout << "Client: All Measurements Success with server!!: " << std::endl;
  std::cout << "Client: closing socket ..." << std::endl;
  std::cout << "Average RTT time: " << elapsed / csp.prob_nums << "s" << std::endl;
  std::cout << std::fixed << "Average Throughput: " << (csp.msg_size*csp.prob_nums) * 8 / elapsed << "bits/s" << std::endl;   // ignore header
  client.closeSocket();
  exit(EXIT_SUCCESS);


  // Start sending message
//  std::string msg;
//  while (std::getline(std::cin, msg)) {
//    // Add ENDMSG for each message
//    msg.append(ENDMSG);
//    if (msg.find("exit\n")  != std::string::npos ) {
//      std::cout << "Client: closing client socket ..." << std::endl;
//      client.closeSocket();
//      exit(EXIT_SUCCESS);
//    }
//    if (sendAll(client.getSocketFd(), msg, msg.length()) < 0 ) {
//      perror("Client: failed to send the message to server, closing socket ...");
//      client.closeSocket();
//      exit(EXIT_FAILURE);
//    }
//    msg.clear();
//    if (receiveAll(client.getSocketFd(), msg) < 0 ) {
//      client.closeSocket();
//      exit(EXIT_FAILURE);
//    }
//    std::cout << "Client: specify message you want to send" << std::endl;
//  }

  return 0;
}