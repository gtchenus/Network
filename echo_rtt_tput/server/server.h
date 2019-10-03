//
// Created by Guanting Chen on 2019/9/28.
//

#ifndef PA1_SERVER_H
#define PA1_SERVER_H


#include <cstdio>
#include <cstring>
#include <iostream>
#include <climits>
#include <map>
#include <mutex>
#include <thread>
#include <sstream>
#include <vector>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <assert.h>
#include <netdb.h>


#define DEFAULTPORT 58000
#define MAXHOSTNAME 255
#define ENDMSG "\n"

enum Phase{
  UNDEFINED = 'n',
  CSP = 's',
  MP = 'm',
  CTP = 't',
};

struct CspHandle {
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
  CspHandle() : CspHandle("", 0, 0, 0) {}
  CspHandle(const std::string type, const uint32_t probs,
         const uint32_t size, const uint32_t delay)
      : protocal_phase(UNDEFINED),
        measure_type(""),
        prob_nums(0),
        msg_size(0),
        server_delay(0) {}
};

struct MpHandle {
  char protocal_phase;
  uint32_t prob_seq;
  uint64_t size_;   // default 0
  std::string payload;

  std::string toString() const {
    std::stringstream ss;
    ss << protocal_phase << " " << prob_seq << " " << payload << ENDMSG;
    return ss.str();
  }
  MpHandle() : MpHandle(0, 0) {}
  MpHandle(const uint32_t seq, const uint32_t size)
      : protocal_phase(UNDEFINED),
        size_(size),
        prob_seq(seq),
        payload("") { }
};

struct CtpHandle {
  char protocal_phase;
  std::string toString() const {
    std::stringstream ss;
    ss << protocal_phase << ENDMSG;
    return ss.str();
  }
  CtpHandle() : protocal_phase(UNDEFINED) {}
};


class Server {
public:
  Server() : Server(DEFAULTPORT) {
    std::cout << "Server: undefined port number name, using 58000" << std::endl;
  }
  Server(const uint32_t port);
  int establish();
  static int handleCSP(int csockfd, const uint32_t &id, CspHandle &csp);
  static int handleMP(int csockfd, const uint32_t &id, const CspHandle &csp, MpHandle &mp);
  static int handleCTP(int csockfd, const uint32_t &id, CtpHandle &ctp);
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

inline std::vector<std::string> parseMsg(const std::string &s, char delim) {
  std::stringstream ss(s);
  std::string item;
  std::vector<std::string> elems;
  while (std::getline(ss, item, delim)) {
    elems.push_back(item);
  }
  return elems;
}
inline void sleep_milliseconds(const uint32_t s) {
  std::this_thread::sleep_for(std::chrono::milliseconds(s));
}
// Used for multi-thread echo
std::mutex mtx;
void echo(int sockfd, uint32_t id, std::map<uint32_t, std::string> *map);
int sendAll(int sockfd, const uint32_t &id, const std::string &msg, uint32_t size);
int receiveAll(int sockfd, const uint32_t &id, std::string &msg);

#endif //PA1_SERVER_H
