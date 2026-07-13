import zlib
import sys

def calculate_checksum(filename):
    with open(filename, 'rb') as f:
        data = f.read()
        return zlib.crc32(data)

files = [
    "V1__create_users_table.sql",
    "V2__create_audit_log_table.sql",
    "V3__enforce_case_insensitive_email_unique.sql",
    "V4__role_column_to_varchar.sql",
    "V5__add_token_version_column.sql",
    "V6__security_and_search_hardening.sql",
    "V7__create_refresh_tokens_table.sql",
    "V8__fix_production_bottlenecks.sql",
    "V9__harden_database_constraints.sql"
]

for f in files:
    path = "src/main/resources/db/migration/" + f
    print(f"{f}: {calculate_checksum(path)}")
