#!/bin/sh

# Pull all status updates from the madras2 machine to ABACUS.

MADRAS_SPOOL_DIR=madras2:/var/spool/abacus
ABACUS_SPOOL_DIR=/opt/abacus/share/abac/out/Import/

rsync -avz --remove-source-files $MADRAS_SPOOL_DIR/* $ABACUS_SPOOL_DIR
