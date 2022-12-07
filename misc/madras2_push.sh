#!/bin/bash

# Push productions notifications from the abacus machine to Madras2.
# Use curl to send HTTP push for each notification. If the request
# returns 200 or 201 all is well and the xml file can be moved to the
# processed dir. Otherwise echo the result which contains a
# description of the problem.

# Sending recorded requests will cause the Madras2 server to encode
# these productions. This can be an expensive operation. Only send
# these request when you are not worried about the load on the Madras2
# server, e.g. in off-hours

MODE=$1
SPOOL_DIR=/opt/abacus/out/SN_Madras2
HOST=madras2
BASE_URL=http://${HOST}/api/abacus
REGEXP="^HTTP/1.1 204 No Content"

#set -x

case $MODE in
    new)
	URL=$BASE_URL/new
	REGEXP="^HTTP/1.1 201 Created"
	FILES="${SPOOL_DIR}/SN1_* ${SPOOL_DIR}/SN10_*"
	;;
    recorded)
	URL=$BASE_URL/recorded
	FILES=$SPOOL_DIR/SN3_*
	;;
    recorded_periodical)
	URL=$BASE_URL/recorded
	FILES=$SPOOL_DIR/SN12_*
	;;
    meta)
	URL=$BASE_URL/metadata
	FILES=$SPOOL_DIR/SNMeta_*
	;;
    status)
	URL=$BASE_URL/status
	FILES=$SPOOL_DIR/SNStatus_*
	;;
    *) echo "Unknown mode: Usage: $0 [new|recorded|recorded_periodical|meta|status]";;
esac

for file in $FILES ; do
    if [ -e $file ] ; then # make sure it isn't an empty match
        RESULT=`curl -i -X 'POST' $URL -H 'accept: application/json' -H 'Content-Type: multipart/form-data' -F "file=@${file};type=text/xml" --silent 2>&1`
y	if echo "$RESULT" | grep -q "$REGEXP"
	then mv $file $SPOOL_DIR/processed/
	else
	    echo "Push '$MODE' to Madras2 for $file failed."
            echo "Moving file to $SPOOL_DIR/invalid/"
            mv "$file" "$SPOOL_DIR"/invalid/
            printf '\n%s\n%s\n\n' "Madras2 response:" "$RESULT"
            grep -E \
               'artikel_nr|identifier|date|title|idVorstufe|process_status' \
               $SPOOL_DIR/invalid/${file##*/}
            echo "========================================="
	fi
    fi
done
