create table if not exists extract_config (
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
  config_payload longtext,
  created_by varchar(100),
  published_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_extract_config_code_version (config_code, version),
  key idx_extract_config_status (status),
  key idx_extract_config_department (department_id),
  key idx_extract_config_document_type (document_type)
);

create table if not exists parse_config (
  id varchar(64) primary key,
  extract_config_id varchar(64),
  engine_code varchar(100),
  output_format varchar(50),
  preprocess_enabled char(1) default '1',
  engine_params longtext,
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists parse_preprocess_step_config (
  id varchar(64) primary key,
  parse_config_id varchar(64),
  step_type varchar(50) not null,
  step_name varchar(100),
  sort_no int,
  enabled char(1) default '1',
  page_ranges varchar(500),
  include_keywords longtext,
  exclude_keywords longtext,
  image_quality varchar(50),
  image_dpi int,
  image_format varchar(20),
  split_page_count int,
  merge_order varchar(50),
  config_json longtext,
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists result_table_config (
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
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_result_table_code (table_code)
);

create table if not exists result_table_column_config (
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
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_result_column (result_table_id, column_name)
);

create table if not exists storage_mapping_profile (
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
  published_at datetime null,
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists extract_field_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  field_code varchar(128) not null,
  field_name varchar(200) not null,
  field_desc varchar(1000),
  extract_required char(1) default '0',
  multiple char(1) default '0',
  sort_no int,
  extract_methods varchar(200),
  strategy_config longtext,
  prompt_hint varchar(1000),
  enabled char(1) default '1',
  unique key uk_extract_field (extract_config_id, field_code)
);

create table if not exists storage_field_mapping_config (
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

create table if not exists storage_unique_constraint_config (
  id varchar(64) primary key,
  mapping_profile_id varchar(64) not null,
  result_table_id varchar(64) not null,
  constraint_name varchar(128) not null,
  unique_columns longtext not null,
  duplicate_scope varchar(50),
  duplicate_strategy varchar(50),
  generate_db_index char(1) default '0',
  sort_no int,
  enabled char(1) default '1',
  description varchar(1000),
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists extract_regex_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  extract_field_config_id varchar(64),
  field_code varchar(128) not null,
  rule_name varchar(200),
  regex_pattern longtext not null,
  regex_group int,
  regex_flags varchar(50),
  sample_text longtext,
  sample_result varchar(1000),
  validation_status varchar(30),
  fail_message varchar(1000),
  sort_no int,
  enabled char(1) default '1',
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists transform_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  rule_code varchar(128),
  rule_name varchar(200) not null,
  rule_type varchar(50) not null,
  sort_no int,
  input_field varchar(128),
  output_field varchar(128),
  output_mode varchar(50),
  condition_enabled char(1) default '0',
  condition_field varchar(128),
  condition_operator varchar(50),
  condition_value varchar(500),
  on_fail varchar(50),
  rule_config longtext,
  enabled char(1) default '1',
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists transform_dict_item_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  match_mode varchar(50),
  source_value varchar(500),
  target_value varchar(500),
  sort_no int,
  enabled char(1) default '1'
);

create table if not exists transform_api_param_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  downstream_service_id varchar(64),
  http_method varchar(20),
  endpoint_override varchar(500),
  request_param_mapping longtext,
  response_value_path varchar(500),
  success_rule varchar(500),
  timeout_seconds int,
  retry_count int,
  auth_mode varchar(50)
);

create table if not exists transform_sql_param_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  datasource_code varchar(100),
  sql_template longtext,
  request_param_mapping longtext,
  result_column varchar(128),
  max_rows int,
  readonly_checked char(1) default '0'
);

create table if not exists validation_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  rule_code varchar(128),
  rule_name varchar(200) not null,
  rule_type varchar(50) not null,
  field_code varchar(128),
  expression longtext,
  severity varchar(30),
  fail_message varchar(1000),
  sort_no int,
  enabled char(1) default '1',
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists review_policy_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  confidence_threshold decimal(5,4),
  reviewer_role_id varchar(100),
  review_assign_mode varchar(50),
  on_validation_warn varchar(50),
  on_validation_block varchar(50),
  enabled char(1) default '1',
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists review_policy_field_config (
  id varchar(64) primary key,
  review_policy_id varchar(64) not null,
  field_code varchar(128) not null,
  force_review char(1) default '0',
  field_confidence_threshold decimal(5,4),
  review_reason varchar(500),
  sort_no int
);

create table if not exists downstream_system_config (
  id varchar(64) primary key,
  system_code varchar(128) not null,
  system_name varchar(200) not null,
  owner_department_id varchar(100),
  default_auth_mode varchar(50),
  default_timeout_seconds int,
  default_retry_count int,
  status varchar(30),
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_downstream_system_code (system_code)
);

create table if not exists downstream_service_config (
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
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_downstream_service_code (service_code)
);

create table if not exists config_push_rule_config (
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
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists config_push_field_mapping (
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

create table if not exists config_permission_scope (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  department_id varchar(100),
  role_id varchar(100),
  permission_type varchar(50),
  data_scope varchar(50),
  custom_scope_expr varchar(1000),
  enabled char(1) default '1',
  created_at datetime not null,
  updated_at datetime not null
);

create table if not exists validation_test_record (
  id varchar(64) primary key,
  test_id varchar(64) not null,
  extract_config_id varchar(64) not null,
  config_version int,
  sample_document_id varchar(64),
  status varchar(30),
  parse_result_path varchar(500),
  extract_result_json longtext,
  validation_result_json longtext,
  storage_preview_json longtext,
  error_message varchar(1000),
  created_by varchar(100),
  created_at datetime not null
);

create table if not exists validation_test_stage_log (
  id varchar(64) primary key,
  test_id varchar(64) not null,
  extract_config_id varchar(64),
  stage varchar(50),
  status varchar(30),
  input_summary varchar(1000),
  output_summary varchar(1000),
  error_message varchar(1000),
  started_at datetime,
  ended_at datetime,
  duration_ms bigint,
  expire_at datetime,
  cleaned char(1) default '0'
);

create table if not exists llm_model_config (
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
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_llm_model_code (model_code),
  key idx_llm_model_status (status),
  key idx_llm_model_default (default_model)
);

create table if not exists ocr_engine_config (
  id varchar(64) primary key,
  engine_code varchar(128) not null,
  engine_name varchar(200) not null,
  engine_type varchar(100) not null,
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
  status varchar(30) not null,
  description varchar(1000),
  created_by varchar(100),
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_ocr_engine_code (engine_code),
  key idx_ocr_engine_status (status),
  key idx_ocr_engine_default (default_engine)
);

create table if not exists document_access_record (
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
  confirmed_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_document_access_trace (trace_id),
  unique key uk_document_access_doc (document_id),
  key idx_document_access_status (access_status, match_status),
  key idx_document_access_source (source_type, source_system),
  key idx_document_access_department (department_id),
  key idx_document_access_config (matched_config_id)
);

create table if not exists extract_task (
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
  failed_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_extract_task_id (task_id),
  key idx_extract_task_trace (trace_id),
  key idx_extract_task_status (status),
  key idx_extract_task_queue (department_id, queue_level, queue_position),
  key idx_extract_task_config (config_id),
  key idx_extract_task_failed (status, failed_at)
);

create table if not exists task_stage_log (
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
  started_at datetime,
  ended_at datetime,
  duration_ms bigint,
  created_at datetime not null,
  key idx_task_stage_log_task (task_id),
  key idx_task_stage_log_trace (trace_id),
  key idx_task_stage_log_status (status)
);
