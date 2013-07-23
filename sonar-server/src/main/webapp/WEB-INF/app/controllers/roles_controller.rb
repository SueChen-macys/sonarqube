#
# Sonar, entreprise quality control tool.
# Copyright (C) 2008-2013 SonarSource
# mailto:contact AT sonarsource DOT com
#
# SonarQube is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# SonarQube is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program; if not, write to the Free Software Foundation,
# Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#
class RolesController < ApplicationController
  helper RolesHelper

  SECTION=Navigation::SECTION_CONFIGURATION
  BULK_LIMIT=1

  before_filter :admin_required
  verify :method => :post, :only => [:set_users, :set_groups], :redirect_to => {:action => 'global'}


  # GET /roles/global
  def global
  end

  # GET /roles/projects
  def projects
    params['pageSize'] = 25
    params['qualifiers'] ||= 'TRK'
    @query_result = Internal.component_api.find(params)

    @available_qualifiers = java_facade.getQualifiersWithProperty('hasRolePolicy').collect { |qualifier| [message("qualifiers.#{qualifier}"), qualifier] }.sort

    # For the moment, we return projects from rails models, but it should be replaced to return java components (this will need methods on ComponentQueryResult to return roles from component)
    @projects = Project.all(
        :include => ['user_roles','group_roles'],
        :conditions => ['kee in (?)', @query_result.components().to_a.collect{|component| component.key()}],
        # Even if components are already sorted, we must sort them again as this SQL query will not keep order
        :order => 'name'
    )
    @components_names = params[:names]
    @components_keys = params[:keys]
    @components_qualifiers = params[:qualifiers]
  end

  # GET /roles/edit_users[?resource=<resource>]
  def edit_users
    @project=Project.by_key(params[:resource]) if params[:resource].present?
    @role = params[:role]
    render :partial => 'edit_users'
  end

  # POST /roles/set_users?users=<users>&role=<role>[&resource=<resource>]
  def set_users
    bad_request('Missing role') if params[:role].blank?
    UserRole.grant_users(params[:users], params[:role], params[:resource])
    render :text => '', :status => 200
  end

  # GET /roles/edit_groups[?resource=<resource>]
  def edit_groups
    @project=Project.by_key(params[:resource]) if params[:resource].present?
    @role = params[:role]
    render :partial => 'edit_groups'
  end

  # POST /roles/set_groups?users=<users>&role=<role>[&resource=<resource>]
  def set_groups
    bad_request('Missing role') if params[:role].blank?
    GroupRole.grant_groups(params[:groups], params[:role], params[:resource])
    render :text => '', :status => 200
  end

  # GET /roles/apply_template_form?criteria
  def apply_template_form
    @permission_templates = Internal.permission_templates.selectAllPermissionTemplates().sort_by {|t| t.name.downcase}.collect {|pt| [pt.name, pt.key]}

    params['qualifiers'] ||= 'TRK'
    params['pageSize'] = 500

    query_result = Internal.component_api.find(params)
    components = query_result.components().to_a
    total_results_count = query_result.paging.total.to_i

    @projects_ids = components.collect{|component| component.getId()}
    @qualifier = params[:qualifiers]

    render :partial => 'apply_template_form',
           :locals => {:project_name => components.size == 1 ? components[0].name : nil,
                       :empty => @projects_ids.nil? || @projects_ids.size == 0,
                       :overflow => total_results_count > BULK_LIMIT}
  end

  # POST /roles/apply_template?criteria
  def apply_template
    verify_post_request
    require_parameters :template_key
    Internal.permissions.applyPermissionTemplate(params)
    redirect_to :action => 'projects'
  end

end
