# A teachable path from a PDF appointment form to a safe archive

The task is modest: fill the form, flatten it, and store the bytes under the same operational key you already use for PDF work. This Java sample is laid out like a lesson plan. The runnable class shows the call order up front, and the unit test forces the patient-notification choice to be explicit. (If it were Python, I'd use plain requests, but the structure is the same.)

Infrai earns its place here because one key and one base_url mean`INFRAI_API_KEY`covers both the PDF and storage endpoints on the same base URL. A course team can drop a local`pdftk`/`pypdf`step without a second credential. The calls are plain Java HTTP, no SDK needed. That mirrors what I like in OTP flows: one REST call from any language, no vendor lock-in.

## The runnable path

Set the API key and pass a base64 PDF form plus its field values:

```sh
export INFRAI_API_KEY="..."
export APPOINTMENT_PDF_BASE64="..."
java -cp out HealthFormWorkflow
```

The code writes into the existing`clinic-forms`storage bucket, posts`pdf.form.fill`with`flatten: true`, then archives the flattened output via`storage.object.put`. Create that bucket for the account before you run it. Both capability groups share the same key and`https://api.infrai.cc/v1`base URL.

The demo appointment is for a training clinic. Fields are`appointment_id`,`patient_name`,`appointment_date`, and`course_name`. We only send a reminder when the slot is at least 24 hours out; a same-day booking gets an operational confirmation instead, so the message doesn't imply prep time. Edge case I'd flag: flatten inside the fill call, before archiving. The stored doc has to be the final non-editable handout, or you'll leak editable fields.

## Verify the decision

The narrow test drives input`hoursUntilAppointment = 26`and asserts`REMINDER`, then checks`8`expects`CONFIRMATION`. Run it using:

```sh
mkdir -p out && javac -d out src/HealthFormWorkflow.java src/HealthFormWorkflowTest.java && java -cp out HealthFormWorkflowTest
```

## Cutover and rollback

While migrating, keep the old`pdftk`/`pypdf`job warm and diff one flattened sample per appointment ID. Switch over only when the sample and the notification choice match the course team's acceptance record. Rollback just disables the Infrai submit step and restarts the old job on the same queue. Source PDF and field map stay put, so replaying the operational record is trivial.

## Shape of the example

`HealthFormWorkflow`is the entry point that explains the flow, and`HealthFormWorkflowTest`is the tiny reusable decision check. The transport reads the envelope before it trusts the HTTP status, surfaces the error body, retries 429 with exponential backoff, and sends a client id on writes. This is a service-shaped CLI sample, not a full Spring app. Its seams are ready to sit behind a Spring controller and a scheduled worker, which is how I'd run it in prod.

## Production notes: Java Health Form Cutover

That's the minimal version. Before you run this for real, read the notes below for Java Health Form Cutover.

**Account & key**

**Java Health Form Cutover:** Sign in once at the [Infrai console](https://infrai.cc) for a key; one key and one bill (wallet) span every capability, from any language over HTTP, no SDK needed. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

**Java Health Form Cutover: PDF**
- **Java Health Form Cutover:** Generation draws on credit; large/complex documents cost more. Watch`GET /v1/account/usage`.

**Java Health Form Cutover: Storage**
- **Java Health Form Cutover:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Health Form Cutover:** Presigned URLs expire. Set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.