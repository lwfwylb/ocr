create table extract_config (
  id varchar(64) primary key,
  config_code varchar(128) not null,
  config_name varchar(200) not null,
  category varchar(100),
  sub_category varchar(100),
  template_type varchar(160),
  document_type varchar(100) not null,
  department_id varchar(100) not null,
  owner_role varchar(100),
  default_priority varchar(20),
  status varchar(30) not null,
  version int not null,
  config_payload clob,
  created_by varchar(100),
  published_at timestamp null,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_extract_config_code_version unique (config_code, version)
);

create index idx_extract_config_status on extract_config(status);
create index idx_extract_config_department on extract_config(department_id);
create index idx_extract_config_document_type on extract_config(document_type);

create table sys_dict_type (
  id varchar(64) primary key,
  dict_code varchar(128) not null,
  dict_name varchar(200) not null,
  usage_scene varchar(200),
  status varchar(30) not null,
  sort_no int,
  remark varchar(1000),
  created_by varchar(100),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_sys_dict_type_code unique (dict_code)
);

create index idx_sys_dict_type_status on sys_dict_type(status);

create table sys_dict_item (
  id varchar(64) primary key,
  dict_code varchar(128) not null,
  item_value varchar(200) not null,
  item_label varchar(200) not null,
  parent_value varchar(200),
  sort_no int,
  enabled char(1) default '1',
  extra_json clob,
  remark varchar(1000),
  created_by varchar(100),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_sys_dict_item_value unique (dict_code, item_value)
);

create index idx_sys_dict_item_dict on sys_dict_item(dict_code, enabled);
create index idx_sys_dict_item_parent on sys_dict_item(dict_code, parent_value);

create table sys_role (
  id varchar(64) primary key,
  role_code varchar(100) not null,
  role_name varchar(200) not null,
  description varchar(500),
  status varchar(30) default 'ENABLED',
  sort_no int default 100,
  created_at timestamp,
  updated_at timestamp,
  constraint uk_sys_role_code unique (role_code)
);
create index idx_sys_role_status on sys_role(status);

create table sys_user (
  id varchar(64) primary key,
  user_code varchar(100),
  user_name varchar(200) not null,
  account varchar(120) not null,
  department_id varchar(120),
  auth_mode varchar(30) default 'LOCAL',
  status varchar(30) default 'ENABLED',
  email varchar(200),
  mobile varchar(60),
  last_login timestamp,
  created_at timestamp,
  updated_at timestamp,
  constraint uk_sys_user_account unique (account)
);
create index idx_sys_user_department on sys_user(department_id);
create index idx_sys_user_status on sys_user(status);

create table sys_user_department_role (
  id varchar(64) primary key,
  user_id varchar(64) not null,
  department_id varchar(120),
  role_id varchar(64) not null,
  created_at timestamp,
  constraint uk_sys_user_dept_role unique (user_id, department_id, role_id)
);
create index idx_sys_user_role_user on sys_user_department_role(user_id);
create index idx_sys_user_role_role on sys_user_department_role(role_id);

create table sys_permission (
  id varchar(64) primary key,
  permission_code varchar(120) not null,
  permission_name varchar(200) not null,
  permission_type varchar(30) not null,
  parent_code varchar(120),
  route_path varchar(300),
  sort_no int default 100,
  status varchar(30) default 'ENABLED',
  created_at timestamp,
  updated_at timestamp,
  constraint uk_sys_permission_code unique (permission_code)
);
create index idx_sys_permission_parent on sys_permission(parent_code);

create table sys_role_permission (
  id varchar(64) primary key,
  role_id varchar(64) not null,
  permission_code varchar(120) not null,
  created_at timestamp,
  constraint uk_sys_role_permission unique (role_id, permission_code)
);
create index idx_sys_role_permission_role on sys_role_permission(role_id);

create table sys_data_policy (
  id varchar(64) primary key,
  policy_name varchar(200) not null,
  subject_type varchar(30) not null,
  subject_id varchar(64),
  subject_name varchar(200),
  data_scope varchar(60) not null,
  allow_export char(1) default '0',
  status varchar(30) default 'ENABLED',
  created_at timestamp,
  updated_at timestamp
);
create index idx_sys_data_policy_subject on sys_data_policy(subject_type, subject_id);
create index idx_sys_data_policy_status on sys_data_policy(status);

create table sys_data_policy_scope (
  id varchar(64) primary key,
  policy_id varchar(64) not null,
  scope_type varchar(60) not null,
  scope_value varchar(300) not null,
  scope_label varchar(300)
);
create index idx_sys_data_policy_scope_policy on sys_data_policy_scope(policy_id);
create index idx_sys_data_policy_scope_type on sys_data_policy_scope(scope_type, scope_value);

create table parse_config (
  id varchar(64) primary key,
  extract_config_id varchar(64),
  engine_code varchar(100),
  output_format varchar(50),
  preprocess_enabled char(1) default '1',
  engine_params clob,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table parse_preprocess_step_config (
  id varchar(64) primary key,
  parse_config_id varchar(64),
  step_type varchar(50) not null,
  step_name varchar(100),
  sort_no int,
  enabled char(1) default '1',
  page_ranges varchar(500),
  include_keywords clob,
  exclude_keywords clob,
  image_quality varchar(50),
  image_dpi int,
  image_format varchar(20),
  split_page_count int,
  merge_order varchar(50),
  config_json clob,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table result_table_config (
  id varchar(64) primary key,
  table_code varchar(128) not null,
  table_name varchar(200) not null,
  table_comment varchar(1000),
  owner_department_id varchar(100),
  storage_datasource varchar(100),
  auto_create_table char(1) default '0',
  auto_add_column char(1) default '0',
  ddl_status varchar(30),
  status varchar(30) not null,
  created_by varchar(100),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_result_table_code unique (table_code)
);

create table result_table_column_config (
  id varchar(64) primary key,
  result_table_id varchar(64) not null,
  column_name varchar(128) not null,
  column_name_cn varchar(200),
  db_type varchar(50) not null,
  type_params varchar(50),
  field_length int,
  field_precision int,
  field_scale int,
  required char(1) default '0',
  default_value varchar(500),
  validation_rule varchar(500),
  sort_no int,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_result_column unique (result_table_id, column_name)
);

create table storage_mapping_profile (
  id varchar(64) primary key,
  profile_code varchar(128) not null,
  profile_name varchar(200) not null,
  extract_config_id varchar(64) not null,
  result_table_id varchar(64) not null,
  save_mode varchar(30),
  primary_key_strategy varchar(100),
  status varchar(30) not null,
  version int not null,
  description varchar(1000),
  created_by varchar(100),
  published_at timestamp null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table extract_field_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  field_code varchar(128) not null,
  field_name varchar(200) not null,
  field_desc varchar(1000),
  extract_required char(1) default '0',
  multiple char(1) default '0',
  sort_no int,
  extract_methods varchar(200),
  strategy_config clob,
  prompt_hint varchar(1000),
  enabled char(1) default '1',
  constraint uk_extract_field unique (extract_config_id, field_code)
);

create table storage_field_mapping_config (
  id varchar(64) primary key,
  mapping_profile_id varchar(64) not null,
  extract_field_config_id varchar(64),
  extract_field_code varchar(128) not null,
  target_column_config_id varchar(64),
  target_column varchar(128) not null,
  multiple char(1) default '0',
  transform_rule_id varchar(64),
  required_for_storage char(1) default '0',
  sort_no int,
  enabled char(1) default '1',
  remark varchar(1000)
);

create table storage_unique_constraint_config (
  id varchar(64) primary key,
  mapping_profile_id varchar(64) not null,
  result_table_id varchar(64) not null,
  constraint_name varchar(128) not null,
  unique_columns clob not null,
  duplicate_scope varchar(50),
  duplicate_strategy varchar(50),
  generate_db_index char(1) default '0',
  sort_no int,
  enabled char(1) default '1',
  description varchar(1000),
  created_at timestamp not null,
  updated_at timestamp not null
);

create table extract_regex_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  extract_field_config_id varchar(64),
  field_code varchar(128) not null,
  rule_name varchar(200),
  regex_pattern clob not null,
  regex_group int,
  regex_flags varchar(50),
  sample_text clob,
  sample_result varchar(1000),
  validation_status varchar(30),
  fail_message varchar(1000),
  sort_no int,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table transform_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  rule_code varchar(128),
  rule_name varchar(200) not null,
  rule_type varchar(50) not null,
  sort_no int,
  input_fields clob,
  output_field varchar(128),
  output_mode varchar(50),
  condition_enabled char(1) default '0',
  condition_field varchar(128),
  condition_operator varchar(50),
  condition_value varchar(500),
  on_fail varchar(50),
  rule_config clob,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table transform_dict_item_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  match_mode varchar(50),
  source_value varchar(500),
  target_value varchar(500),
  sort_no int,
  enabled char(1) default '1'
);

create table transform_api_param_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  downstream_service_id varchar(64),
  http_method varchar(20),
  endpoint_override varchar(500),
  request_param_mapping clob,
  response_value_path varchar(500),
  success_rule varchar(500),
  timeout_seconds int,
  retry_count int,
  auth_mode varchar(50)
);

create table transform_sql_param_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  datasource_code varchar(100),
  sql_template clob,
  request_param_mapping clob,
  result_column varchar(128),
  max_rows int,
  readonly_checked char(1) default '0'
);

create table validation_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  rule_code varchar(128),
  rule_name varchar(200) not null,
  rule_type varchar(50) not null,
  field_code varchar(128),
  expression clob,
  severity varchar(30),
  fail_message varchar(1000),
  sort_no int,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table review_policy_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  confidence_threshold decimal(5,4),
  reviewer_role_id varchar(100),
  review_assign_mode varchar(50),
  on_validation_warn varchar(50),
  on_validation_block varchar(50),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table review_policy_field_config (
  id varchar(64) primary key,
  review_policy_id varchar(64) not null,
  field_code varchar(128) not null,
  force_review char(1) default '0',
  field_confidence_threshold decimal(5,4),
  review_reason varchar(500),
  sort_no int
);

create table downstream_system_config (
  id varchar(64) primary key,
  system_code varchar(128) not null,
  system_name varchar(200) not null,
  owner_department_id varchar(100),
  default_auth_mode varchar(50),
  default_timeout_seconds int,
  default_retry_count int,
  status varchar(30),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_downstream_system_code unique (system_code)
);

create table downstream_service_config (
  id varchar(64) primary key,
  system_id varchar(64) not null,
  service_code varchar(128) not null,
  service_name varchar(200) not null,
  purpose varchar(100),
  service_type varchar(50),
  endpoint varchar(500),
  http_method varchar(20),
  auth_mode varchar(50),
  timeout_seconds int,
  retry_count int,
  response_success_rule varchar(1000),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_downstream_service_code unique (service_code)
);

create table config_push_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  downstream_service_id varchar(64) not null,
  push_enabled char(1) default '1',
  push_trigger varchar(50),
  push_scope varchar(50),
  push_mode varchar(30),
  idempotent_key_expr varchar(500),
  fail_strategy varchar(50),
  sort_no int,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table config_push_field_mapping (
  id varchar(64) primary key,
  push_rule_id varchar(64) not null,
  source_field varchar(128),
  downstream_field varchar(128),
  required char(1) default '0',
  default_value varchar(500),
  transform_expr varchar(1000),
  sort_no int,
  enabled char(1) default '1'
);

create table config_permission_scope (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  department_id varchar(100),
  role_id varchar(100),
  permission_type varchar(50),
  data_scope varchar(50),
  custom_scope_expr varchar(1000),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table validation_test_record (
  id varchar(64) primary key,
  test_id varchar(64) not null,
  extract_config_id varchar(64) not null,
  config_version int,
  sample_document_id varchar(64),
  status varchar(30),
  parse_result_path varchar(500),
  extract_result_json clob,
  validation_result_json clob,
  storage_preview_json clob,
  error_message varchar(1000),
  created_by varchar(100),
  created_at timestamp not null
);

create table validation_test_stage_log (
  id varchar(64) primary key,
  test_id varchar(64) not null,
  extract_config_id varchar(64),
  stage varchar(50),
  status varchar(30),
  input_summary varchar(1000),
  output_summary varchar(1000),
  error_message varchar(1000),
  started_at timestamp,
  ended_at timestamp,
  duration_ms bigint,
  expire_at timestamp,
  cleaned char(1) default '0'
);

create table llm_model_config (
  id varchar(64) primary key,
  model_code varchar(128) not null,
  model_name varchar(200) not null,
  provider varchar(100) not null,
  base_url varchar(500) not null,
  api_key_secret_ref varchar(200),
  model_identifier varchar(200) not null,
  temperature decimal(5,3) default 0.100,
  max_tokens int default 4096,
  timeout_seconds int default 120,
  retry_count int default 1,
  json_schema_required char(1) default '1',
  default_model char(1) default '0',
  status varchar(30) not null,
  description varchar(1000),
  created_by varchar(100),
  created_at timestamp not null,
  updated_at timestamp not null
);

create unique index uk_llm_model_code on llm_model_config (model_code);
create index idx_llm_model_status on llm_model_config (status);
create index idx_llm_model_default on llm_model_config (default_model);

create table prompt_template_config (
  id varchar(64) primary key,
  template_type varchar(30) not null,
  template_name varchar(100) not null,
  template_content clob not null,
  status varchar(30) not null,
  updated_by varchar(100),
  created_at timestamp not null,
  updated_at timestamp not null
);

create unique index uk_prompt_template_type on prompt_template_config (template_type);

create table ocr_engine_config (
  id varchar(64) primary key,
  engine_code varchar(128) not null,
  engine_name varchar(200) not null,
  engine_type varchar(100) not null,
  adapter_type varchar(50),
  provider varchar(100) not null,
  base_url varchar(500) not null,
  auth_mode varchar(50),
  api_key_secret_ref varchar(200),
  default_engine char(1) default '0',
  priority int default 100,
  timeout_seconds int default 120,
  retry_count int default 2,
  supported_file_types varchar(500),
  output_format varchar(50) default 'Markdown',
  max_pages_per_call int,
  engine_params_json clob,
  status varchar(30) not null,
  description varchar(1000),
  created_by varchar(100),
  created_at timestamp not null,
  updated_at timestamp not null
);

create unique index uk_ocr_engine_code on ocr_engine_config (engine_code);
create index idx_ocr_engine_status on ocr_engine_config (status);
create index idx_ocr_engine_default on ocr_engine_config (default_engine);

create table document_access_record (
  id varchar(64) primary key,
  trace_id varchar(128) not null,
  document_id varchar(128) not null,
  task_id varchar(128),
  file_name varchar(500) not null,
  file_type varchar(50),
  file_size bigint,
  storage_path varchar(1000),
  source_type varchar(50) not null,
  source_system varchar(200),
  business_no varchar(200),
  department_id varchar(100) not null,
  category varchar(100),
  sub_category varchar(100),
  template_type varchar(160),
  document_type varchar(100),
  priority varchar(20),
  match_status varchar(30) not null,
  access_status varchar(30) not null,
  matched_config_id varchar(64),
  matched_config_name varchar(200),
  matched_config_version int,
  match_message varchar(1000),
  confirm_comment varchar(1000),
  created_by varchar(100),
  confirmed_at timestamp null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create unique index uk_document_access_trace on document_access_record (trace_id);
create unique index uk_document_access_doc on document_access_record (document_id);
create index idx_document_access_status on document_access_record (access_status, match_status);
create index idx_document_access_source on document_access_record (source_type, source_system);
create index idx_document_access_department on document_access_record (department_id);
create index idx_document_access_config on document_access_record (matched_config_id);

create table extract_task (
  id varchar(64) primary key,
  task_id varchar(128) not null,
  trace_id varchar(128) not null,
  document_id varchar(128) not null,
  access_record_id varchar(64),
  config_id varchar(64),
  config_name varchar(200),
  config_version int,
  file_name varchar(500) not null,
  file_type varchar(50),
  file_size bigint,
  storage_path varchar(1000),
  source_type varchar(50),
  source_system varchar(200),
  business_no varchar(200),
  department_id varchar(100) not null,
  category varchar(100),
  sub_category varchar(100),
  template_type varchar(160),
  document_type varchar(100),
  priority varchar(20) not null,
  status varchar(30) not null,
  current_stage varchar(100),
  progress int default 0,
  queue_level varchar(20),
  queue_name varchar(200),
  queue_capacity int,
  queue_position int,
  waiting_minutes int,
  estimated_start_at varchar(100),
  manual_accelerated char(1) default '0',
  dispatch_reason varchar(1000),
  error_code varchar(100),
  error_message varchar(1000),
  failed_stage varchar(100),
  retry_count int default 0,
  max_retry int default 3,
  failed_at timestamp null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create unique index uk_extract_task_id on extract_task (task_id);
create index idx_extract_task_trace on extract_task (trace_id);
create index idx_extract_task_status on extract_task (status);
create index idx_extract_task_queue on extract_task (department_id, queue_level, queue_position);
create index idx_extract_task_config on extract_task (config_id);
create index idx_extract_task_failed on extract_task (status, failed_at);

create table task_stage_log (
  id varchar(64) primary key,
  task_id varchar(128) not null,
  trace_id varchar(128) not null,
  stage_code varchar(50) not null,
  stage_name varchar(100) not null,
  status varchar(30) not null,
  input_summary varchar(1000),
  output_summary varchar(1000),
  error_code varchar(100),
  error_message varchar(1000),
  started_at timestamp,
  ended_at timestamp,
  duration_ms bigint,
  created_at timestamp not null
);

create index idx_task_stage_log_task on task_stage_log (task_id);
create index idx_task_stage_log_trace on task_stage_log (trace_id);
create index idx_task_stage_log_status on task_stage_log (status);

create table document_parse_result (
  id varchar(64) primary key,
  task_id varchar(128) not null,
  trace_id varchar(128) not null,
  document_id varchar(128) not null,
  engine_code varchar(128),
  parse_text clob,
  parse_markdown_path varchar(1000),
  page_count int,
  status varchar(30) not null,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_document_parse_task unique (task_id)
);

create index idx_document_parse_trace on document_parse_result (trace_id);

create table document_artifact (
  id varchar(64) primary key,
  trace_id varchar(128) not null,
  task_id varchar(128),
  document_id varchar(128),
  parent_id varchar(64),
  artifact_type varchar(32) not null,
  stage_code varchar(32) not null,
  file_name varchar(255),
  file_ext varchar(32),
  mime_type varchar(128),
  storage_path varchar(1000),
  preview_path varchar(1000),
  file_size bigint,
  checksum varchar(128),
  page_no int,
  page_range varchar(128),
  sort_no int,
  status varchar(32),
  metadata_json clob,
  created_at timestamp not null,
  updated_at timestamp not null
);

create index idx_document_artifact_trace on document_artifact (trace_id);
create index idx_document_artifact_task on document_artifact (task_id);
create index idx_document_artifact_parent on document_artifact (parent_id);
create index idx_document_artifact_type on document_artifact (artifact_type, stage_code);

create table document_artifact_step (
  id varchar(64) primary key,
  trace_id varchar(128) not null,
  task_id varchar(128),
  step_code varchar(64),
  step_name varchar(128),
  step_type varchar(64),
  input_artifact_ids clob,
  output_artifact_ids clob,
  config_json clob,
  status varchar(32),
  error_message varchar(1000),
  started_at timestamp,
  ended_at timestamp,
  duration_ms bigint,
  created_at timestamp not null
);

create index idx_artifact_step_trace on document_artifact_step (trace_id);
create index idx_artifact_step_task on document_artifact_step (task_id);
create index idx_artifact_step_code on document_artifact_step (step_code);

create table extract_result_record (
  id varchar(64) primary key,
  task_id varchar(128) not null,
  trace_id varchar(128) not null,
  document_id varchar(128) not null,
  config_id varchar(64),
  result_json clob,
  confidence_json clob,
  overall_confidence decimal(8,6),
  need_review char(1) default '0',
  status varchar(30) not null,
  field_count int,
  target_table varchar(200),
  mapping_profile varchar(200),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_extract_result_task unique (task_id)
);

create index idx_extract_result_trace on extract_result_record (trace_id);
create index idx_extract_result_status on extract_result_record (status);
create index idx_extract_result_config on extract_result_record (config_id);

create table extract_review_log (
  id varchar(64) primary key,
  task_id varchar(128) not null,
  trace_id varchar(128) not null,
  action varchar(50) not null,
  before_json clob,
  after_json clob,
  comment_text varchar(1000),
  reviewer varchar(100),
  created_at timestamp not null
);

create index idx_extract_review_task on extract_review_log (task_id);
create index idx_extract_review_trace on extract_review_log (trace_id);
create index idx_extract_review_action on extract_review_log (action);

create table storage_result_record (
  id varchar(64) primary key,
  task_id varchar(128) not null,
  trace_id varchar(128) not null,
  document_id varchar(128) not null,
  config_id varchar(64),
  target_table varchar(200) not null,
  mapping_profile varchar(200),
  storage_json clob not null,
  unique_key_json clob,
  storage_status varchar(30) not null,
  duplicate_strategy varchar(50),
  error_message varchar(1000),
  stored_by varchar(100),
  stored_at timestamp,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_storage_result_task unique (task_id)
);

create index idx_storage_result_trace on storage_result_record (trace_id);
create index idx_storage_result_table on storage_result_record (target_table);
create index idx_storage_result_status on storage_result_record (storage_status);
create index idx_storage_result_config on storage_result_record (config_id);

create table model_call_log (
  id varchar(64) primary key,
  call_id varchar(128) not null,
  trace_id varchar(128),
  task_id varchar(128),
  config_id varchar(64),
  call_type varchar(30) not null,
  stage_code varchar(50),
  stage_name varchar(100),
  provider varchar(100),
  model_code varchar(128),
  model_name varchar(200),
  request_summary varchar(1000),
  response_summary varchar(1000),
  prompt_preview clob,
  input_tokens int,
  output_tokens int,
  duration_ms bigint,
  status varchar(30) not null,
  error_message varchar(1000),
  created_at timestamp not null,
  constraint uk_model_call_log_call unique (call_id)
);

create index idx_model_call_log_trace on model_call_log (trace_id);
create index idx_model_call_log_task on model_call_log (task_id);
create index idx_model_call_log_type_status on model_call_log (call_type, status);
create index idx_model_call_log_model on model_call_log (model_code);
create index idx_model_call_log_created on model_call_log (created_at);

create table downstream_push_record (
  id varchar(64) primary key,
  push_id varchar(128) not null,
  trace_id varchar(128) not null,
  task_id varchar(128) not null,
  document_id varchar(128),
  config_id varchar(64),
  target_system varchar(200),
  service_code varchar(128),
  service_name varchar(200),
  push_method varchar(50),
  trigger_type varchar(50),
  idempotent_key varchar(500),
  request_payload clob,
  response_payload clob,
  status varchar(30) not null,
  retry_count int default 0,
  max_retry int default 3,
  response_code varchar(100),
  response_message varchar(1000),
  pushed_at timestamp,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_downstream_push_id unique (push_id)
);

create index idx_downstream_push_trace on downstream_push_record (trace_id);
create index idx_downstream_push_task on downstream_push_record (task_id);
create index idx_downstream_push_status on downstream_push_record (status);
create index idx_downstream_push_service on downstream_push_record (service_code);
create index idx_downstream_push_idempotent on downstream_push_record (idempotent_key);
