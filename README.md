# Guide for USERS of this app

1. The MySQL database must be named: `pamigay_db` AND It must contain these eight tables strictly:
      - `district`
      - `barangay`
      - `beneficiary`
      - `staff`
      - `crisis`
      - `payout`
      - `receipt`
      - `announcement`

   This is required for the app to run. The tables may be empty or may contain a millions amount of data. As long as the database exists and all eight tables are present, the app will run fully.

2. Open XAMPP , then Click the 'START' button for MySQL.

3. Then make sure your MySQL Workbench is running. Try this query: (it must show the eight tables in number 1)

      ```sql
      USE pamigay_db;
      SHOW TABLES;
      ```

4. Run the application (PamigayApp.java).

5. When the app opens, enter:
      - Host: `localhost`
      - Port: `3306`
      - Database: `pamigay_db`
      - Username: `root` (root is default)
      - Password: (no need if you entered none before)
