# -*- encoding: utf-8

from flask import jsonify

from ingest_manager import app
from report_ingest_status import report_ingest_status
from request_new_ingest import send_new_ingest_request


@app.route('/ingests/<guid>')
def route_report_ingest_status(guid):
    result = report_ingest_status(
        dynamodb_resource=app.config['dynamodb_resource'],
        table_name=app.config['dynamodb_table_name'],
        guid=guid
    )
    return jsonify(result)


@app.route('/ingests', methods=['POST'])
def route_request_new_ingest():
    return '', 202
