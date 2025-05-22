import boto3
import json
import logging
import os
from urllib.error import HTTPError, URLError
from urllib.parse import quote_plus
from urllib.request import Request, urlopen

logger = logging.getLogger()
logger.setLevel(logging.INFO)

color_map = {"SUCCEEDED": "good", "FAILED": "danger"}
emoji_map = {"SUCCEEDED": ":rocket:", "FAILED": ":sob:"}



def handler(event, _):
    logger.info(f"Got event: {event}")
    # this could need more stringent conditions if other folks use AWS Batch in the same AWS account
    # I guess we'll walk that bridge when we get there
    if event["detail"]["status"] not in ("SUCCEEDED", "FAILED"):
        logger.info(f"Skipping event state: {event['detail']['status']}")
        return


    # Check if the job is a child job by looking for an array index
    if "arrayProperties" in event["detail"] and "index" in event["detail"]["arrayProperties"]:
        if event["detail"]["arrayProperties"]["index"] is not None:
            logger.info(f"Skipping child job with index: {event['detail']['arrayProperties']['index']}")
            return

    job_name = event["detail"]["jobName"]
    execution_id = event["detail"]["jobId"]
    statusText = f"{event['detail']['status']}"

    scenario = [x for x in event["detail"]["container"]["environment"] if x["name"]=="OUTPUT_SCENARIO"]
    jobNameEnv = [x for x in event["detail"]["container"]["environment"] if x["name"]=="JOB_NAME"]
    debug = [x for x in event["detail"]["container"]["environment"] if x["name"]=="DEBUG"]

    output_bucket = [x for x in event["detail"]["container"]["environment"] if x["name"]=="JOB_OUTPUT_BUCKET"]

    try:
        scenario = scenario[0]['value']
    except IndexError:
        scenario = "OUTPUT_SCENARIO_ERROR"

    try:
        jobNameEnv = jobNameEnv[0]['value']
    except IndexError:
        scenario = "JOB_NAME_ERROR"

    try:
        debug = debug[0]['value']
        # Check if debug is set to "true" and return if so
        if debug.lower() == "true":
            print("Debug mode detected, exiting method.")
            return
    except IndexError:
        debug = "DEBUG_NOT_SET"  # Default value or handling if DEBUG is not set

    emoji = emoji_map[statusText]


    slack_message = {
        "username": "MATSim Notifications",
        "as_user": False,
        "link_names": True,
        "icon_emoji": emoji,
        "text": f"Matsim Job {job_name} {statusText}",
        "attachments": [
            {
                "title": "Batch job link",
                "title_link": f"https://eu-central-1.console.aws.amazon.com/batch/home?region=eu-central-1#jobs/detail/{execution_id}",
                "color": color_map.get(event["detail"]["status"], "#c0c0c0"),
            },
            {
                "title": "Output S3",
                "title_link": f"https://s3.console.aws.amazon.com/s3/buckets/{output_bucket}/{scenario}/{jobNameEnv}/?region=eu-central-1&tab=objects",
                "color": color_map.get(event["detail"]["status"], "#c0c0c0"),
            }
        ],
    }

    if "arrayProperties" in event["detail"]:
        array_status_summary = event["detail"]["arrayProperties"]["statusSummary"]
        array_summary_text = "\n".join([f"{k}: {v}" for k, v in array_status_summary.items()])
        slack_message["attachments"].append(
            {
                "title": "Array Job Status Summary",
                "text": array_summary_text,
                "color": color_map.get(event["detail"]["status"], "#c0c0c0"),
            }
        )


    if os.environ.get("SLACK_CHANNEL"):
        slack_message["channel"] = os.environ.get("SLACK_CHANNEL")

    req = Request(
        os.environ["SLACK_HOOK_URL"], json.dumps(slack_message).encode("utf8")
    )
    logger.info(f"Sending request to Slack: {slack_message} ")
    try:
        response = urlopen(req)
        response.read()
        logger.info("Message posted")
    except HTTPError as e:
        logger.error(f"Request failed:{e.code}, {e.reason}")
    except URLError as e:
        logger.error(f"Server connection failed: {e.reason}")