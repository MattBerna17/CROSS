# CROSS - Centralized Order Book Exchange Service

**Author**: Matthew Bernardi  
**Academic Year**: 2024/2025  
**Course**: Reti e Laboratorio (LAB2425)

---

## 📌 Project Overview

CROSS (an **exChange oRder bOokS Service**) is a simplified centralized exchange system for cryptocurrency trading, focusing on BTC/USD pairs. It emulates the core mechanisms behind order matching, execution, and market dynamics—similar to what real-world platforms like Binance or Coinbase use.

The project consists of a **multi-threaded server** and an **interactive client**, communicating via TCP and UDP, designed entirely in **Java**. Orders supported include **Market**, **Limit**, and **Stop Orders**, which are processed following a **price/time priority matching algorithm**.

---

## ⚙️ Features

### ✔️ Order Types
- **Market Orders**: Executed immediately at the best available price. If conditions aren't met, the order is rejected.
- **Limit Orders**: Executed only if price conditions are satisfied; otherwise queued for future matching.
- **Stop Orders**: Turn into Market Orders once a defined stop condition is met.

### ✔️ Core Client Features
- Register/Login/Logout
- Place Market, Limit, and Stop Orders (both Ask and Bid)
- Cancel pending orders
- Request price history (with daily OHLC data)
- Real-time notifications via UDP on order executions

### ✔️ Server Responsibilities
- Handle multiple clients concurrently using a thread pool
- Maintain persistent storage of users and executed orders (in JSON format)
- Match orders fairly and efficiently
- Send asynchronous UDP notifications on trade executions
- Periodically check and execute pending Stop Orders

---

### 🛠️ Compilation and Execution

You can compile and run the server/client using the provided bash scripts:

```bash
./server.sh   # Launch server
./client.sh   # Launch client
```

---

## 🗃️ Data Structures

- `ConcurrentSkipListSet<Order>`: for open ask and bid orders, ordered by price and timestamp
- `ConcurrentLinkedQueue<User>`: stores all registered users
- `ConcurrentLinkedQueue<Order>`: history of executed orders
- `ConcurrentLinkedQueue<Order>`: pending stop orders
- `LinkedBlockingQueue<Runnable>`: server-side task queue

Synchronization is ensured using Java's `synchronized` blocks when accessing shared structures or writing to files.

---

## 🧵 Thread Architecture

- **Client**:
  - `ClientMain`: Main thread, handles CLI interaction and TCP messages
  - `ClientUDP`: Listens for server-side asynchronous notifications

- **Server**:
  - `ServerMain`: Accepts new TCP connections
  - `ServerTask`: Handles one client per thread
  - `ServerUDP`: Sends UDP notifications, shared among ServerTasks

---

## 📁 Project Structure
The `config/` folder contains the main configuration files and the `client.sh` and `server.sh` execution bash scripts, meanwhile the `src/` folder contains the source code files.

--
