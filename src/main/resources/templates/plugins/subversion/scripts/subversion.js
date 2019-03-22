jQuery(function($) {
    var subversion = {
        getContextPath : function() {
            return contextPath;
        },

        getParamsFromFieldSet : function(fieldSet) {
            var params = {};

            fieldSet.find("input").each(function() {
                params[this.name] = this.value;
            });

            return params;
        },

        initShowMoreButton : function(moreButton, moreUrlPath, dataType, callback) {
            if(moreButton.data('initialised') === undefined)
            {
                var moreButtonContainer = moreButton.parent();
                var params = this.getParamsFromFieldSet(moreButtonContainer.children("fieldset"));

                moreButton.click(function(event) {
                    moreButton.hide();
                    moreButtonContainer.append("<img src='" + subversion.getContextPath() + "/images/icons/wait.gif'/>");

                    $.ajax({
                        type : "GET",
                        url : subversion.getContextPath() + moreUrlPath,
                        data : params,
                        success : function(someHtml) {
                            moreButtonContainer.remove();
                            callback(someHtml);

                        }
                    });
                    event.preventDefault();
                    event.stopPropagation();
                    return false;
                });
            }
        },

        createTemporaryInvisibleDiv : function() {
            return $(document.createElement("div")).hide();
        },

        initShowMoreButtonInIssueTab : function(moreButton) {jsObject["content"]
            var currentIssueKey = $("a[id^='issue_key_']").text() ||  $("#key-val").text();
            if (currentIssueKey)
                moreButton.parent().children("fieldset").find("input[name='issueKey']").attr("value", currentIssueKey);

                this.initShowMoreButton(
                    moreButton,
                    "/browse/" + currentIssueKey,
                    "html",
                    function(html) {
                        var issueActionsContainer = $("#issue_actions_container");
                        var tempCommitsDiv = subversion.createTemporaryInvisibleDiv();

                        tempCommitsDiv.html(html);
                        tempCommitsDiv.find("div.issuePanelContainer table, div.issuePanelContainer div.plugin_subversion_showmore_issuetab").appendTo(issueActionsContainer);

                        subversion.initShowMoreButtonInIssueTab(issueActionsContainer.find("input.plugin_subversion_showmore_issuetab_button"));
                    }
                );

            moreButton.data('initialised', true);
        },

        initShowMoreButtonInProjectTab : function(moreButton) {
            var selectedVersion = $("select[name='selectedVersion'] option:selected");
            var buttonFieldSet = moreButton.parent().children("fieldset");
            var params = this.getParamsFromFieldSet(buttonFieldSet);

            if (selectedVersion.length == 1) {
                buttonFieldSet.find("input[name='versionId']").attr("value", selectedVersion.attr("value"));
            }

            this.initShowMoreButton(
                moreButton,
                "/browse/" + params["projectKey"],
                "html",
                function(html) {
                    var projectCommitsContainer = $("div.projectPanel table.plugin_subversion_projectcommits_table");
                    var tempCommitsDiv = subversion.createTemporaryInvisibleDiv();

                    tempCommitsDiv.html(html);

                    var commitsHtml = tempCommitsDiv.find("table.plugin_subversion_projectcommits_table");
                    var commitsHtmlTableBody = commitsHtml.children("tbody");
                    
                    if (commitsHtmlTableBody.length > 0)
                        commitsHtml = commitsHtmlTableBody;

                    commitsHtml.appendTo(projectCommitsContainer);

                    subversion.initShowMoreButtonInProjectTab(projectCommitsContainer.find("input.plugin_subversion_showmore_projectab_button"));
                }
            );
        },

        initVersionSelectForm : function(theForm) {
            theForm.find("select[name='selectedVersion']").change(function() {
                theForm.submit();
            });
        }
    };

    if (JIRA.ViewIssueTabs && JIRA.ViewIssueTabs.onTabReady && $.isFunction(JIRA.ViewIssueTabs.onTabReady))
    {
        JIRA.ViewIssueTabs.onTabReady(function() {
            $("input.plugin_subversion_showmore_issuetab_button").each(function() {
                subversion.initShowMoreButtonInIssueTab($(this));
            });
        });
    }

    $(".plugin_subversion_showmore_issuetab_button").click(function() {
        $(this).data('initialised', false);
    });

    $("input.plugin_subversion_showmore_projectab_button").each(function() {
        subversion.initShowMoreButtonInProjectTab($(this));
    });

    subversion.initVersionSelectForm($("form.plugin_subversion_versionselect_form"));
});