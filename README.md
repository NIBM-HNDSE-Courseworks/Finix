# Finix: Dual-Role Personal Finance Management System

A comprehensive mobile application developed for the **Data Management 2** coursework at the **National Institute of Business Management (NIBM)**.

Finix is designed to help individuals track expenses, budgets, and savings, utilizing a dual-database architecture for both offline accessibility and centralized, robust analytics.

## 🌟 Key Features

* **Expense & Income Tracking:** Seamless logging of all financial transactions.
* **Budget Monitoring:** Create, track, and monitor monthly spending limits across various categories.
* **Savings Goal Management:** Set financial targets and visualize progress toward savings goals.
* **Insightful Financial Reports:** Generation of key reports (e.g., Monthly expenditure analysis, Budget adherence) from the central database.
* **Robust Data Security:** Implementation of security and privacy measures for sensitive financial data in both local and central databases.

---

## 💾 Dual-Database Architecture & Synchronization

This project demonstrates a critical dual-role approach to data management, utilizing two distinct database platforms:

| Component | Database System | Purpose & Justification |
| :--- | :--- | :--- |
| **Local / Offline** | **SQLite** | Provides fast, reliable **offline data storage** and a seamless user experience even without an internet connection. |
| **Central / Cloud** | **Oracle Database** | Used for **centralized financial data management**, aggregation, advanced analytics, and long-term storage. |

### Synchronization Mechanism

A core component of Finix is the synchronization logic, which ensures data consistency:

* **Mechanism:** Designs mechanisms to periodically sync local SQLite data to the centralized Oracle Database.
* **Conflict Resolution:** Includes logic to handle conflict resolution during the synchronization process to maintain data integrity.

---

## 🛠 Technology Stack

### Mobile Application (Front-end & Local Data)

* **Platform:** Android (Java)
* **Local Database:** **Room Persistence Library** (SQLite implementation)
* **Architecture:** MVVM (Model-View-ViewModel)

### Central Database (Back-end)

* **Database:** **Oracle Database** (Centralized Management)
* **Server-side Logic:** **PL/SQL Procedures** for centralized CRUD operations and report generation.

---

## 📂 Project Structure (Data Layer Focus)

The primary data persistence components are housed in the `data` package:

| Component | Files | Description |
| :--- | :--- | :--- |
| **Entities** | `Transaction.java`, `Budget.java`, `SavingsGoal.java`, `SynchronizationLog.java` | Defines the database structure and enforces constraints (Primary Key, Foreign Key, Not Null, Unique). |
| **DAOs** | `TransactionDao.java`, `BudgetDao.java`, etc. | Defines all SQLite CRUD operations (`@Insert`, `@Query`, `@Update`). |
| **Database** | `FinixDatabase.java` | The main singleton class for the Room (SQLite) database instance. |
| **Repository** | `FinixRepository.java` | Abstracts local (SQLite) and remote (Oracle) data access, providing a clean API to ViewModels. |

---

## 🚀 Getting Started (Simulation Note)

To run this project, ensure you have the following services running or simulated:

1.  **Mobile App:** Load the project in Android Studio and run it on an emulator or physical device.
2.  **Oracle Connection:** A configured connection to the Oracle Database instance to test the synchronization and report generation features.

**Note::** The Oracle connection and PL/SQL procedures are demonstrated via the synchronization logic and generated reports as defined in the deliverables.
