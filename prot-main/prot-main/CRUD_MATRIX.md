# Pa-MIGAY Prototype CRUD Matrix

**Requirement #4 CRUD matrix**  
This matrix describes the operations exposed by the current Java prototype. Table order follows the relational schema reference supplied for the project: DISTRICT, BARANGAY, BENEFICIARY, CRISIS, STAFF, PAYOUT, ANNOUNCEMENT, RECEIPT.

**Legend:** C = create a record; R = read, list, search, or view records; U = update an existing record; — = no direct operation. A check mark means the prototype provides that operation for the table.

## CRUD matrix

| Table | C | R | U |
|---|:---:|:---:|:---:|
| DISTRICT | ✓ | ✓ | ✓ |
| BARANGAY | ✓ | ✓ | ✓ |
| BENEFICIARY | ✓ | ✓ | ✓ |
| CRISIS | ✓ | ✓ | ✓ |
| STAFF | ✓ | ✓ | ✓ |
| PAYOUT | ✓ | ✓ | ✓ |
| ANNOUNCEMENT | ✓ | ✓ | ✓ |
| RECEIPT | ✓ | ✓ | ✓ |

The assignment's matrix template has C, R, and U columns. Delete behavior is documented separately below because deletion is available sparingly and has record-dependency safeguards. Reading a related table to fill a dropdown or display a joined value supports another screen; it does not by itself mean that screen creates or updates the related table.

## Screen, actor, and operation summary

| UI | Intended actor(s) | Main interface | Operations and remarks |
|---|---|---|---|
| UI-01 Overview | Administrator; Program Coordinator | Summary cards, recent distributions, quick actions | **R** for dashboard summaries. Quick actions open create forms for beneficiaries, payouts, and claims; the overview itself does not edit dashboard data. |
| UI-02 Beneficiaries | Administrator; Beneficiary Records Clerk | Searchable beneficiary list; add/edit form with category and barangay selection | **C/R/U** for BENEFICIARY. A matching name and contact number triggers a duplicate warning. The app blocks deletion while a claim references the beneficiary. Avoid exposing personal contact details in exports. |
| UI-03 Locations | Administrator | Barangays/Districts switcher; searchable lists and add/edit forms | **C/R/U** for DISTRICT and BARANGAY. District and barangay names are checked for duplicates. A barangay must reference an existing district. Deletion is blocked while the app finds dependent barangays, beneficiaries, or payouts. |
| UI-04 Crisis Records | Administrator; Program Coordinator | Searchable crisis list and date-validated form | **C/R/U** for CRISIS. Deletion is blocked while a payout references the crisis. One crisis may be associated with multiple payouts. |
| UI-05 Staff | Administrator | Searchable staff list and contact/role form | **C/R/U** for STAFF. The app checks for duplicate staff contact information. Deletion is blocked while a payout references the staff member. |
| UI-06 Payouts | Administrator; Program Coordinator | Searchable schedule and form for date, amount, location, target group, district, crisis, and staff | **C/R/U** for PAYOUT. The form requires the linked district, crisis, and staff records and a positive amount. Deletion is blocked while a claim or announcement references the payout. |
| UI-07 Claims | Payout Clerk; Administrator | Searchable claims list and form for payout, beneficiary, date/time, amount, and status | **C/R/U** for RECEIPT (shown in the app as “Claims”). The app warns when the same beneficiary already has a claim for the payout. Deletion asks for confirmation and displays a policy warning; the app does not verify that a claim is an erroneous duplicate. |
| UI-08 Announcements | Administrator; Communications Coordinator | Searchable notice list and form for content, date/time, platform, and linked payout | **C/R/U** for ANNOUNCEMENT. The related payout must exist. Deletion requires confirmation. |
| UI-09 Reports | Administrator; Program Coordinator; Auditor | Report selector, optional date range, results table, and CSV export | **R** only. Reports join or aggregate existing records; this screen has no create, update, or delete controls. |

## Delete behavior and safeguards

Deletion is intentionally not marked as a routine operation in the C/R/U matrix. The prototype does expose delete actions, but they should be used only when appropriate and after reviewing linked records.

| Table | Current prototype behavior for delete |
|---|---|
| DISTRICT | Confirmation required. Blocked if a BARANGAY or PAYOUT references the district. |
| BARANGAY | Confirmation required. Blocked if a BENEFICIARY references the barangay. |
| BENEFICIARY | Confirmation required. Blocked if a RECEIPT/claim references the beneficiary. |
| CRISIS | Confirmation required. Blocked if a PAYOUT references the crisis. |
| STAFF | Confirmation required. Blocked if a PAYOUT references the staff member. |
| PAYOUT | Confirmation required. Blocked if a RECEIPT/claim or ANNOUNCEMENT references the payout. |
| ANNOUNCEMENT | Confirmation required before deletion. |
| RECEIPT | Confirmation required, with a warning to remove only an erroneous duplicate under policy. The app does not automatically determine whether the selected claim is a duplicate. |

## Reading this matrix correctly

- **C and U are separate actions:** adding a new row is C; saving changes to an existing row is U.
- **R includes search and joined display:** for example, the beneficiary list displays its barangay and district names, and the payout list displays linked district, crisis, and staff details.
- **Reports and dashboard values are read-only:** joining several tables for a report does not create or update those records.
- **Actor labels describe intended users:** this prototype has no sign-in or role-based access control, so the actor list is not enforced as a permission system.
- **The matrix reflects this prototype's screens and code:** it does not claim database-level constraints that have not been verified.
