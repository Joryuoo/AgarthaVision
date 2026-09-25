# Patient PII & Clinical Data Privacy Position

**Pre-validation Architecture Decision Record & Institutional Privacy Position**  
**Task reference:** PB-26 (`86d4be4w4`)  
**Effective date:** September 20, 2026  
**Status:** Approved & Signed  
**Scope:** Android Client (`app/`), Supabase Backend (`supabase/migrations/0001_init.sql`), Web Admin Console, and Sprint 1 MVP Validation Deliverables (D1–D5).

---

## 1. Executive Summary & Problem Context

Before the patient-records architectural refactor (PB-01 through PB-26), AgarthaVision stored no Personally Identifiable Information (PII) or Sensitive Personal Information (SPI). A sample consisted solely of an unparented microscopic JPEG, a GPS fix, and species detections (`supabase/migrations/legacy-dev/0001_init.sql:1-40`).

Following the refactor, the data architecture transitions to a clinical hierarchy:
```
User (Medtech/Admin) ──▶ Patient ──▶ Session (Smear) ──▶ Sample (Field) ──▶ Detection
```
The application now captures and stores:
* `lastname` (`patients.lastname`: NOT NULL text)
* `firstname` (`patients.firstname`: NOT NULL text)
* `middle_name` (`patients.middle_name`: nullable text)
* `sex` (`patients.sex`: `'M'` / `'F'`)
* `birthdate` (`patients.birthdate`: epoch millis at midnight `Asia/Manila`)
* `psgc_barangay_code` (`patients.psgc_barangay_code`: 10-digit zero-padded canonical code)

All of these fields are joined directly to parasitological examination findings, low-power field (LPF) egg density ranges, and diagnostic reports (`supabase/migrations/0001_init.sql:91-112`, `app/src/main/java/com/agarthavision/data/local/entity/PatientEntity.kt:38-82`).

Under the **Philippine Data Privacy Act of 2012 (Republic Act No. 10173)**, §3(l), this constitutes **Sensitive Personal Information** regarding an individual's health, age, and sex. Crucially, the primary target population for Soil-Transmitted Helminth (STH) surveillance in the Philippines consists of **school-age children (minors aged 1–14)** participating in DepEd/DOH mass deworming programs.

The Sprint 1 validation deliverables — [D1 MVP validation Google Form](https://app.clickup.com/t/86d4akvjf), [D2 validation framework PDF](https://app.clickup.com/t/86d4akvj9), and [D3 facilitated validation sessions](https://app.clickup.com/t/86d4akvjq) — place this software in front of practicing medical technologists. Entering real patient names on mobile devices during validation would inadvertently commit the project to handling live SPI without required institutional ethics clearances or statutory safeguards.

This document establishes the project's formal written position on patient privacy, data retention, at-rest security, statutory compliance, and the validation protocol.

---

## 2. Technical & Architectural Reality (As-Built Audit)

A factual audit of the codebase confirms five operational realities:

1. **Unencrypted Storage at Rest:**
   - The local SQLite/Room database (`AgarthaDatabase.db`) is unencrypted plain text. There is no SQLCipher wrapper and no Android Keystore-backed encryption key (`core/database/AgarthaDatabase.kt:52-105`).
   - Microscopic field JPEGs are written as plain image files to internal app-private storage (`data/local/SampleImageStore.kt:15-21` writes to `/data/user/0/com.agarthavision/files/users/{userId}/samples/{sampleId}.jpg`).
2. **Database-Only Row Level Security (RLS):**
   - Post-refactor patient visibility is controlled at the Supabase database layer via the `patient_users` join table (`supabase/migrations/0001_init.sql:117-122`, `:360-388`).
   - While RLS provides database tenancy enforcement, it provides zero protection for data resident on an unlocked, lost, or compromised physical Android handset.
3. **Shared Clinical Hardware:**
   - Rural health unit (RHU) and barangay microscopy stations operate shared devices. While mandatory authentication (`core/session/SessionManager.kt:29`) attributes work to a logged-in user, cached Supabase credentials satisfy the authentication gate indefinitely. Whoever holds the unlocked phone possesses the permissions of the signed-in medtech.
4. **Constraint C8 ("Nothing is deleted"):**
   - Under `docs/constraints.md` C8, verified samples are soft-deleted (`samples.deleted_at`), never purged. Supabase Storage enforces an insert/select-only policy with deliberately no DELETE policy (`supabase/migrations/0003_storage_rls.sql:45-46`). This retention model serves model retraining but presents a compliance conflict if identifiable personal data is retained indefinitely.
5. **PDF Diagnostic Reports in Public Shared Storage:**
   - Generated session reports are saved to shared public external storage in `Documents/AgarthaVision/` via `DocumentsReportFileStore.kt:15-45` using `MediaStore` or direct filesystem access.
   - Any third-party application on the handset with storage read permissions, or any workstation connected via USB Media Transfer Protocol (MTP), can read these patient-identifying PDF reports without authenticating to AgarthaVision.

---

## 3. The Six Position Statements

### Position 1: Informed Consent & Pediatric Safeguards
* **Clinical Standard:** If real patients are ever processed, informed consent must be collected prior to record creation. Patients (or guardians) must be explicitly informed:
  1. What data is collected (demographics, residence barangay, stool microscopy images, parasite counts).
  2. The purpose of data processing (clinical decision support and epidemiological mapping).
  3. That AI model output is an assistive screening aid, not an autonomous diagnosis, and is subject to human medical technologist verification (`docs/constraints.md` C7).
  4. How long data is retained and their statutory rights under RA 10173 (access, correction, erasure).
* **Consent Collector:** The licensed medical technologist or clinic health worker conducting the examination.
* **Recording Mechanism:** Physical signed consent forms archived in the clinic's local document repository, referenced by an anonymized clinical consent code.
* **Guardian Route for Minors:** For all patients under 18 years of age, consent must be executed by a parent or legal guardian. Where feasible (children aged 7–17), pediatric assent must accompany parental consent.
* **MVP Position:** Because AgarthaVision currently has no digital consent capture, signature verification, or audit logging module, **no real patient may be entered into the system during the current MVP stage.**

### Position 2: Data Retention vs. Constraint C8 (ML Corpus Decoupling)
* **The Conflict:** Constraint C8 mandates permanent retention of microscopy images and false-positive bounding boxes to preserve the model retraining corpus. Conversely, RA 10173 §19 requires that personal data be retained only for as long as necessary to fulfill its clinical diagnostic purpose.
* **The Resolution — Separation of Identity from Tensors:**
  - Machine learning model retraining requires **optical frames, bounding boxes, labels, and species classifications** (`detections`, `sample_species_findings`). It **does not require patient names, birthdates, or contact details**.
  - Long-term model retraining artifacts must be **strictly decoupled from the patient index**.
  - Any image or detection record exported for retraining or retained in the indefinite ML corpus must be irrevocably de-identified, stripping `patient_id` and replacing it with an opaque random study hash.
* **Clinical Retention Schedule:** Patient clinical records in Supabase shall adhere to the Department of Health (DOH) standard retention period for clinical diagnostic records (typically 5 to 10 years). An automated retention job (Phase 2) will purge or anonymize personal demographic fields from expired patient records while preserving anonymous detection rows under C8.

### Position 3: Validation Sessions Specifically (Deliverables D1–D5)
* **Binding Decision:** **Deliverables D1, D2, D3, D4, and D5 shall strictly and exclusively utilize SYNTHETIC / DE-IDENTIFIED PATIENTS.**
* **Protocol:**
  - Facilitators and medical technologists shall use fictitious patient demographic profiles (e.g., "Patient Alpha", "Subject Test 01", standard test birthdates, and sample barangays).
  - No real patient names, real birthdates, or identifying clinical records may be entered into mobile devices during facilitated validation sessions or self-service APK testing.
  - Physical fecal smear slides evaluated during facilitated sessions (D3) must be pre-existing anonymized laboratory reference specimens labeled solely with a specimen code (e.g., `SLIDE-2026-001`), with no link to living patient identities.
* **Operational Benefit:** This policy completely removes patient PII protection from the validation critical path, ensuring compliance with institutional ethics standards without requiring an emergency Institutional Review Board (IRB) clinical trial clearance prior to sprint completion.

### Position 4: At-Rest Encryption & Compensating Controls
* **Scope:** Database-level encryption (SQLCipher) and encrypted report file storage are **out of scope for Phase 1 MVP** and are scheduled for Phase 2 production hardening.
* **Compensating Controls:**
  1. *Synthetic Data Invariant:* Because only synthetic patient profiles are entered on validation devices, no actual patient SPI is exposed at rest in SQLite or `SampleImageStore`.
  2. *OS-Level Application Sandbox:* On Android, internal storage (`context.filesDir`) is sandboxed by Linux kernel UIDs, preventing access by non-root applications.
  3. *Hardware Device Policy:* All validation test devices must have hardware lock screen security (PIN, passphrase, or biometric) and Android Full-Disk Encryption (FDE) or File-Based Encryption (FBE) enabled.
  4. *Shared Storage Mitigation:* Facilitators must execute a cleanup routine immediately following validation sessions, deleting generated PDFs from `Documents/AgarthaVision/` and clearing application cache.

### Position 5: Philippine Data Privacy Act (RA 10173) Compliance
* **Applicability:** AgarthaVision handles Sensitive Personal Information under RA 10173 §3(l).
* **Academic Exemption Assessment (§4(c)):**
  - Section 4(c) exempts personal information processed for academic or scientific research only to the extent necessary to achieve the research objective and provided adequate security safeguards are maintained.
  - Operating the system in an operational clinic with live patient identification exceeds the academic exemption threshold and classifies the operating entity as a Personal Information Controller (PIC).
* **Prerequisites for Live Clinical Deployment:** Prior to processing real patient records in any operational healthcare setting:
  1. The deploying institution must register the Data Processing System with the National Privacy Commission (NPC) if processing sensitive personal information of 1,000+ individuals.
  2. A designated Data Protection Officer (DPO) must be appointed.
  3. A formal Data Sharing Agreement (DSA) must be executed between Cebu Institute of Technology - University and the partner clinic/hospital.
  4. Institutional Ethics Committee (IEC) review and approval must be secured.

### Position 6: Web Admin Scope & Privilege Inheritance
* **Architectural Surface:** Under `supabase/migrations/legacy-dev/0009_storage_admin_read.sql:22-30` and `supabase/migrations/0001_init.sql:366`, accounts with `profiles.role = 'admin'` inherit global read access across all patient records, sessions, samples, detections, and storage objects.
* **Access Safeguards:**
  1. *Role Minimization:* The `admin` role is restricted to faculty project advisers and certified institutional administrators.
  2. *Authentication Hardening:* Multi-Factor Authentication (MFA) is mandatory for administrative accounts in Supabase.
  3. *Surveillance Aggregation:* The administrative choropleth map and epidemiology analytics must read aggregated prevalence figures (`barangay_prevalence()`) rather than rendering individual patient rosters by default.

---

## 4. Institutional Sign-Off

The position outlined above has been evaluated and approved by the clinical and academic leads.

| Role | Signatory | Decision & Capacity | Date |
|---|---|---|---|
| **Clinical Consultant** | **Dr. Bayron** | Approved — Validation restricted to synthetic/de-identified records; clinical protocol required before live patient capture | September 20, 2026 |
| **Project Adviser** | **Faculty Adviser, Computer Science** | Approved — Academic research scope confirmed; compliance with RA 10173 established via synthetic data mandate | September 20, 2026 |
| **Development Lead** | **Joseph Victor Novabos** | Recorded — Architectural alignment verified; constraints C8 and C10 cross-referenced | September 20, 2026 |

---

## 5. Verification Checklist

* [x] Written position document published on doc shelf (`docs/patient-pii-position.md`).
* [x] Referenced from `docs/constraints.md` C8 (data retention separation) and C10 (PII leakage & at-rest security).
* [x] Named signatories, capacities, and approval dates recorded.
* [x] Validation deliverables D1, D2, and D3 explicitly declare the use of synthetic / de-identified patient data.

---

## 6. Out of Scope (Follow-up Tasks)

1. Implementation of SQLCipher encrypted SQLite driver for Android Room database (Phase 2).
2. Android Keystore-backed scoped storage encryption for PDF reports (Phase 2).
3. Automated Supabase de-identification retention cron worker (Phase 2).
4. Patient consent document logging table and digital signature capture flow (Phase 2).
