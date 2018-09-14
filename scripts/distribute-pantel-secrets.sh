#!/bin/bash

# This script finds directories where pantel-prod.json is gitignored in and copies
# the PANTEL_SECRETS_FILE environment variable into these directories.
# These files are needed for the docker compose acceptance tests.

#### sanity check
if [ -z "${PANTEL_SECRETS_FILE}" ] ; then
  echo "ERROR: PANTEL_SECRETS_FILE env var is empty. Aborting!"
  exit 1
fi
####

echo; echo "======> Creating pantel-prod.json file, using the env variable PANTEL_SECRETS_FILE"
for LOCATION in $(find . -name .gitignore  -exec grep pantel-prod.json  '{}' '+' ); do
  DIR_NAME=$(dirname $LOCATION)
  echo "Creating secrets file: ${DIR_NAME}/pantel-prod.json ..."
  echo ${PANTEL_SECRETS_FILE} | base64 -d >  ${DIR_NAME}/pantel-prod.json
  ls -l ${DIR_NAME}/pantel-prod.json
done
echo ''