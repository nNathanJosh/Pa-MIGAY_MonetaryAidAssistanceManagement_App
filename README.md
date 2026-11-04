# User Guide

## Database requirements

The application requires a MySQL database named `pamigay_db` with these eight tables:

- `district`
- `barangay`
- `beneficiary`
- `staff`
- `crisis`
- `payout`
- `receipt`
- `announcement`

The tables may be empty or contain a large amount of data. The database and all eight tables must exist for the application to run.

## Start MySQL and verify the tables

1. Open XAMPP and click **Start** next to **MySQL**.
2. Open MySQL Workbench and connect to your local MySQL server.
3. Run these queries to check that the database contains all eight tables:

   ```sql
   USE pamigay_db;
   SHOW TABLES;
   ```

## Run the application

1. Run `PamigayApp.java`.
2. When prompted for the MySQL connection details, enter:
   | --- | --- |
   | Host | `localhost` |
   | Port | `3306` |
   | Database | `pamigay_db` |
   | Username | `root` (the default XAMPP username) |
   | Password | Leave blank if you have not set a MySQL password |
