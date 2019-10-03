//
// Created by Guanting Chen on 2019/9/28.
//

#ifndef PA1_CLIENT_H
#define PA1_CLIENT_H

#include <stdexcept>
#include <cstdio>
#include <cstring>
#include <iostream>
#include <sstream>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <netdb.h>
#include <assert.h>
#include <sys/time.h>


#define DEFAULTPORT 58000
#define ENDMSG "\n"

enum Phase{
  START = 'n',
  CSP = 's',
  MP = 'm',
  CTP = 't',
};

struct CspMsg {
  char protocal_phase;
  std::string measure_type;
  uint32_t prob_nums;   // default 0
  uint32_t msg_size;    // default 1
  uint32_t server_delay;    // default 0

  std::string toString() const {
    std::stringstream ss;
    ss << protocal_phase << " " << measure_type << " " << prob_nums << " "
       << msg_size << " " << server_delay << ENDMSG;
    return ss.str();
  }
  CspMsg() : protocal_phase(CSP), msg_size(1) {}
  CspMsg(const std::string type, const uint32_t probs,
          const uint32_t size, const uint32_t delay)
   : protocal_phase(CSP),
     measure_type(type),
     prob_nums(probs),
     msg_size(size),
     server_delay(delay) {}
};

struct MpMsg {
  char protocal_phase;
  uint32_t prob_seq;
  uint64_t size_;   // default 10
  std::string payload;

  std::string toString() const {
    std::stringstream ss;
    ss << protocal_phase << " " << prob_seq << " " << payload << ENDMSG;    // parsing: actual size = msg_size + 1
    return ss.str();
  }
  MpMsg() : MpMsg(0, 1) {}
  MpMsg(const uint32_t seq, const uint32_t size)
   : protocal_phase(MP),
     size_(size),
     prob_seq(seq) {
       payload.resize(size, 'x');
     }
};

struct CtpMsg {
  char protocal_phase;
  std::string toString() const {
    std::stringstream ss;
    ss << protocal_phase << ENDMSG;
    return ss.str();
  }
  CtpMsg() : protocal_phase(CTP) {}
};

class Client {
public:
  Client() : Client("localhost", DEFAULTPORT) {
    std::cout << "Client: undefined host name, using localhost with port: 58000" << std::endl;
  }
  Client(const char *host, const uint32_t port);
  int createConnection();
  int issueCSP(const CspMsg &msg);
  int startMP(MpMsg &msg, const uint32_t probs);
  int issueCTP();
  int getSocketFd() const {return client_sockfd;}
  sockaddr_in getSocketAddress() const {return client_addr;}
  void setPhase(Phase p) {phase = p;}
  Phase getPhase() const {return phase;}
  void closeSocket() {close(client_sockfd);}

private:
  int client_sockfd;  // 3: listening socket
  sockaddr_in client_addr;
  const char *hostname;
  Phase phase;    // current phase
};


inline void parseSocket(sockaddr_in *socket_addr, std::string &ip, std::string &port) {
  ip = inet_ntoa(socket_addr->sin_addr);
  port = std::to_string(ntohs(socket_addr->sin_port));
}
inline int parseProtocalCmd(std::string &type, uint32_t &probs,
    uint32_t &size, uint32_t &delay);


int sendAll(int sockfd, const std::string &msg, uint32_t size);
int receiveAll(int sockfd, std::string &msg);

#endif //PA1_CLIENT_H
