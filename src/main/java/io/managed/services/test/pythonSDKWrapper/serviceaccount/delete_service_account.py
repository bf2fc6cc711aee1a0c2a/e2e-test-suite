import os
import time

import argparse
import time
import rhoas_service_accounts_mgmt_sdk
from rhoas_service_accounts_mgmt_sdk.api import service_accounts_api
from rhoas_service_accounts_mgmt_sdk.model.error import Error
from rhoas_service_accounts_mgmt_sdk.model.red_hat_error_representation import RedHatErrorRepresentation
from pprint import pprint
import auth.rhoas_auth as auth

parser = argparse.ArgumentParser()
parser.add_argument('--service_account_id', help='The ID of the Kafka instance', required=True)
parser.add_argument('--env_file', help='The env file', required=False, default='x.config.env')
args = parser.parse_args()

offline_token = os.environ.get('OFFLINE_TOKEN')

token = {}
token = auth.get_access_token(offline_token)

host_uri = os.environ.get('OBSERVATORIUM_OIDC_ISSUER_URL')
configuration = rhoas_service_accounts_mgmt_sdk.Configuration(
    host = host_uri,
    access_token = token['access_token']
)

with rhoas_service_accounts_mgmt_sdk.ApiClient(configuration) as api_client:
    api_instance = service_accounts_api.ServiceAccountsApi(api_client)
    id = args.service_account_id  # str | The ID of record
    try:
        api_response = api_instance.delete_service_account(id)
        pprint(api_response)
    except rhoas_service_accounts_mgmt_sdk.ApiException as e:
        print("Exception when calling SecurityApi->delete_service_account_by_id: %s\n" % e)