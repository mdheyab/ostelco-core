#!/bin/bash

set -e
# IMPORTER_URL=http://127.00.1:8080/importer

#
## Todo:
##   o Refactor using methods, adding comments and structure
##   o Make it work (again).
##   o add invocation of map_subscribers.sh in the pseudoanonymizer-pod to get
##     a result back, using that for generating the yaml.
##   o Declare victory with respect to closing the loop, merge the branch,
##     then start improving the loop on a daily basis.




###
### VALIDATING AND PARSING COMMAND LINE PARAMETERS
### 


#
#  Get command line parameter, which should be an existing
#  directory in which to store the results
#

TARGET_DIR=$1
if [[ -z "$TARGET_DIR" ]] ; then
    echo "$0  Missing parameter"
    echo "usage  $0 target-dir"
    exit 1
fi

if [[ ! -d "$TARGET_DIR" ]] ; then
    echo "$0  $TARGET_DIR is not a directory"
    echo "usage  $0 target-dir"
    exit 1
fi



###
### PRELIMINARIES
###


# Be able to die from inside procedures

trap "exit 1" TERM
export TOP_PID=$$

function die() {
   kill -s TERM $TOP_PID
}

#
# Check for dependencies being satisfied
#

DEPENDENCIES="gcloud kubectl gsutil"

for dep in $DEPENDENCIES ; do
   if [[ -z $(which $dep) ]] ; then
     echo "ERROR: Could not find dependency $dep"
   fi
done


#
#  Figure out relevant parts of the environment and check their
#  sanity.
#

PROJECT_ID=$(gcloud config get-value project)

if [[ -z "$PROJECT_ID" ]] ; then
    echo "ERROR: Unknown google project ID"
    exit 1
fi

EXPORTER_PODNAME=$(kubectl get pods | grep exporter- | awk '{print $1}')
if [[ -z "$EXPORTER_PODNAME" ]] ; then
    echo "ERROR: Unknown exporter podname"
    exit 1
fi



###
### COMMUNICATION WITH EXPORTER SCRIPTS RUNNING IN A KUBERNETES PODS
###

#
# Run named script on the inside of the kubernetes exporter pod,
# put the output from running that script into a temporary file, return the
# name of that temporary file as the result of running the function.
# The second argument is the intent of the invocation, and is used
# when producing error messages:
#
#    runScriptOnExporterPod  /export_data.sh "export data"
#
function runScriptOnExporterPod {
    if [[ $# -ne 2 ]] ; then
	echo "$0 ERROR:  runScriptOnExporterPod requires exactly two parameters"
    fi
    local scriptname=$1
    local intentDescription=$2

    #TEMPFILE="$(mktemp /tmp/abc-script.XXXXXX)"
    # XXX Also should be lowercase
    TEMPFILE="tmpfile.txt"
    
    kubectl exec -it "${EXPORTER_PODNAME}" -- /bin/bash -c "$scriptname" > "$TEMPFILE"
    
    # Fail if the exec failed
    retVal=$?
    if [[ $retVal -ne 0 ]]; then
	echo "ERROR: Failed to $intentDescription"
	cat $TEMPFILE
	rm $TEMPFILE
	die
    fi

    # Return result by setting resutlvar to be the temporary filename
    echo $TEMPFILE
}


#
# Create a data export batch, return a string identifying that
# batch.  Typical usage:
#    EXPORT_ID=$(exportDataFromExporterPod)
#
function exportDataFromExporterPod {
    local tmpfilename="$(runScriptOnExporterPod /export_data.sh "export data")"
    if [[ -z "$tmpfilename" ]] ; then
	echo "$0 ERROR: Running the runScriptOnExporterPod failed to return the name of a resultfile."
	die
    fi
    
    local exportId="$(grep "Starting export job for" $tmpfilename | awk '{print $5}' |  sed 's/\r$//' )"
    
    if [[ -z "$exportId" ]] ; then
	echo "$0  Could not get export  batch from exporter pod"
    fi
    rm $tmpfilename
    echo $exportId
}

function mapPseudosToUserids {
    local tmpfile="$(runScriptOnExporterPod /map_subscribers.sh "mapping pseudoids to subscriber ids")"
    # XXX Should doe some more checking here before deleting the file
    # rm $tmpfile
}

#
# Generate the Google filesystem names of components associated with
# a particular export ID:   Typical usage
# 
#    PURCHASES_GS="$(gsExportCsvFilename "ab234245cvsr" "purchases")"

function gsExportCsvFilename {
    if [[ $# -ne 2 ]] ; then
	echo "$0 ERROR:  gsExportCsvFilename requires exactly two parameters, got '$@'"
	die
    fi
    
    local exportId=$1
    local componentName=$2
    if [[ -z "$exportId" ]] ; then
       echo "$0 ERROR:  gsExportCsvFilename got a null exportId"
       die
    fi
    if [[ -n "$componentName" ]] ; then
	componentName="-$componentName"
    fi
    
    echo "gs://${PROJECT_ID}-dataconsumption-export/${exportId}${componentName}.csv"
}



function importedCsvFilename {
    if [[ $# -ne 3 ]] ; then
	echo "$0 ERROR:  importedCsvFilename requires exactly three parameters, got $@"
	die
    fi

    local exportId=$1
    local importDirectory=$2
    local componentName=$3

    if [[ -z "$exportId" ]] ; then
       echo "$0 ERROR:  importedCsvFilename got a null exportId"
       die
    fi

    if [[ -z "$importDirectory" ]] ; then
       echo "$0 ERROR:  importDirectory got a null exportId"
       die
    fi

    if [[ -n "$componentName" ]] ; then
	componentName="-$componentName"
    fi
    
    echo "${importDirectory}/${exportId}${componentName}.csv"
}


###
###  MAIN SCRIPT
### 



EXPORT_ID="$(exportDataFromExporterPod)"
echo "EXPORT_ID = $EXPORT_ID"
if [[ -z "$EXPORT_ID" ]] ; then
    echo "$0 ERROR: Could not determine export id"
fi

#
# Copy all the  export artifacts from gs:/ to local filesystem storage
#

for component in "purchases" "sub2msisdn" "" ; do

    source="$(gsExportCsvFilename "$EXPORT_ID" "$component")"
    if [[ -z "$source" ]] ; then
	echo "$0 ERROR: Could not determine source file for export component '$component'"
    fi
    
    destination="$(importedCsvFilename "$EXPORT_ID" "$TARGET_DIR" "$component")"
    if [[ -z "$destination" ]] ; then
	echo "$0 ERROR: Could not determine destination file for export component '$component'"
    fi
    
    gsutil cp "$source" "$destination"
done


##
##  Generate a sample segment by just ripping out
##  all the subscriber IDs in the sub2msisdn file.
##
##  This is clearly not a realistic scenario, much to simple
##  but it is formally correct so it will serve as a placeholder
##  until we get something more realistic.
##

SEGMENT_TMPFILE_PSEUDO="$(importedCsvFilename "$EXPORT_ID" "$TARGET_DIR" "tmpsegment-pseudo")"
awk -F, '!/^subscriberId/{print $1'} $(importedCsvFilename "$EXPORT_ID" "$TARGET_DIR" "sub2msisdn") > $SEGMENT_TMPFILE_PSEUDO


##
## Convert from pseudos to actual IDs
##


RESULTSEG_PSEUDO_BASENAME="resultsegment-pseudoanonymized"
RESULTSEG_CLEARTEXT_BASENAME="resultsegment-cleartext"
RESULT_SEGMENT_PSEUDO_GS="$(gsExportCsvFilename "$EXPORT_ID" "$RESULTSEG_PSEUDO_BASENAME")"
RESULT_SEGMENT_CLEAR_GS="$(gsExportCsvFilename "$EXPORT_ID" "$RESULTSEG_CLEARTEXT_BASENAME")"
RESULT_SEGMENT_CLEAR="$(importedCsvFilename "$EXPORT_ID" "$TARGET_DIR"  "$RESULTSEG_CLEARTEXT_BASENAME")"
RESULT_SEGMENT_SINGLE_COLUMN="$(importedCsvFilename "$EXPORT_ID" "$TARGET_DIR"  "$RESULTSEG_CLEARTEXT_BASENAME")"

# Copy the  segment pseudo file to gs

gsutil cp $SEGMENT_TMPFILE_PSEUDO $RESULT_SEGMENT_PSEUDO_GS

# Then run the script that will convert it into a none-anonymized
# file and fetch the results from gs:/
mapPseudosToUserids

gsutil cp "$RESULT_SEGMENT_CLEAR_GS" "$RESULT_SEGMENT_CLEAR"

echo "Just placed the results in the file $RESULT_SEGMENT_CLEAR"
# Then extract only the column we need (the real userids)

awk -F, '!/^pseudoId/{print $2'} $RESULT_SEGMENT_CLEAR > $RESULT_SEGMENT_SINGLE_COLUMN


##
## Generate the yaml output
##


IMPORTFILE_YML=tmpfile.yml

cat > $IMPORTFILE_YML <<EOF
producingAgent:
  name: Simple agent
  version: 1.0

offer:
  id: test-offer
  # use existing product
  products:
    - 1GB_249NOK
  # use existing segment

segment:
    id: test-segment
    subscribers:
EOF

# Adding the list of subscribers in clear text (indented six spaces
# with a leading "-" as per YAML list syntax.
awk '{print "      - " $1}'  $RESULT_SEGMENT_SINGLE_COLUMN >> $IMPORTFILE_YML 

## Send it to the importer
echo curl --data-binary @$IMPORTFILE_YML $IMPORTER_URL

## And whatever else, some obviousl missing.
rm $SEGMENT_TMPFILE_PSEUDO
rm $SEGMENT_TMPFILE_CLEAR
