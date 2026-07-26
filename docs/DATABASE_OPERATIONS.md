# Payment database operations

This runbook defines the minimum database controls established by PAY-08. It does
not authorize live payment traffic.

## Runtime baseline

Use PostgreSQL 15 with a dedicated least-privilege database role. Supply the
following through the deployment secret store:

```text
PAYMENT_DATABASE_URL=jdbc:postgresql://<database-host>:5432/<database>?sslmode=verify-full
PAYMENT_DATABASE_USERNAME=<runtime-role>
PAYMENT_DATABASE_PASSWORD=<secret>
```

`sslmode=verify-full`, Flyway, Hibernate `validate`, the disabled H2 console and
startup ledger reconciliation are fail-closed production requirements. The
server certificate must chain to a trusted CA and match the database hostname.
Do not put credentials in the JDBC URL, repository, image or logs.

PostgreSQL 15.18 is the tested baseline. Review minor-version release notes,
backup compatibility and restoration evidence before upgrading. The application
container does not contain PostgreSQL administration tools.

## Migration procedure

1. Take and verify a restorable backup.
2. Review migration SQL and its checksum in the exact application revision.
3. Apply the release to a production-like isolated database first.
4. Start Payment Service and require Flyway validation plus startup reconciliation
   to pass.
5. Deploy using the platform's controlled rollout and monitor startup failures.

Flyway migrations are forward-only after release. Do not edit an applied
migration or use Hibernate schema mutation. If a migration fails, stop the
rollout, preserve logs without secrets, and either deliver a reviewed forward
repair migration or restore the pre-change backup into a new database.

## Backup

The platform owner must define schedule, retention, encryption, off-host storage,
access control and alerting under PAY-15. A portable logical backup can be taken
with:

```bash
pg_dump \
  --host=<database-host> \
  --username=<backup-role> \
  --format=custom \
  --file=payment-<UTC-timestamp>.dump \
  <database-name>
```

Record the application revision, Flyway schema version, PostgreSQL version,
timestamp, encrypted-object location and checksum beside the backup. Never place
a dump in source control or an application container.

## Restore rehearsal

Restore only into a newly created, access-restricted database:

```bash
createdb \
  --host=<restore-host> \
  --username=<restore-admin-role> \
  <new-restore-database>

pg_restore \
  --host=<restore-host> \
  --username=<restore-role> \
  --dbname=<new-restore-database> \
  --no-owner \
  payment-<UTC-timestamp>.dump
```

Then:

1. run Flyway validation against the restored database;
2. start the exact Payment Service revision with startup reconciliation enabled;
3. require every wallet to reconcile and compare wallet/entry counts with the
   backup record;
4. verify the append-only trigger rejects an attempted ledger update in the
   isolated rehearsal;
5. destroy the rehearsal database through the approved recoverable process.

Never restore over the live database. Never disable reconciliation to make a
restore appear healthy. A divergence is an incident and must be investigated
before traffic is allowed.

The automated PostgreSQL test performs the same essential proof: reviewed
migrations, persistence after reconnect, database invariants, append-only
enforcement, `pg_dump`, restore into a second database, Flyway validation and
balance reconstruction.
