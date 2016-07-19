/*
 * Copyright (c) 2002-2016, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.workflow.modules.upload.services;

import fr.paris.lutece.plugins.asynchronousupload.service.AbstractAsynchronousUploadHandler;
import fr.paris.lutece.plugins.workflow.modules.upload.business.task.TaskUploadConfig;
import fr.paris.lutece.plugins.workflowcore.service.config.ITaskConfigService;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.util.AppException;
import fr.paris.lutece.util.filesystem.UploadUtil;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;


/**
 * The Class TaskUploadAsynchronousUploadHandler.
 */
public class TaskUploadAsynchronousUploadHandler extends AbstractAsynchronousUploadHandler
{
    private static final String PREFIX_ENTRY_ID = "upload_value_";
    private static final String HANDLER_NAME = "taskUploadAsynchronousUploadHandler";

    // Error messages
    private static final String ERROR_MESSAGE_UNKNOWN_ERROR = "module.workflow.upload.message.unknownError";
    private static final String ERROR_MESSAGE_TO_MANY_FILE = "module.workflow.upload.message.tomamyFile";
    private static final String BEAN_TASK_ASYNCHRONOUS_UPLOAD_HANDLER = "workflow-upload.taskUploadAsynchronousUploadHandler";

    /** <sessionId,<fieldName,fileItems>> */
    /** contains uploaded file items */
    private static Map<String, Map<String, List<FileItem>>> _mapAsynchronousUpload = new ConcurrentHashMap<String, Map<String, List<FileItem>>>(  );
    @Inject
    @Named( TaskUpload.BEAN_UPLOAD_CONFIG_SERVICE )
    private ITaskConfigService _taskUploadConfigService;

    /**
    * Get the handler
    * @return the handler
    */
    public static TaskUploadAsynchronousUploadHandler getHandler(  )
    {
        return SpringContextService.getBean( BEAN_TASK_ASYNCHRONOUS_UPLOAD_HANDLER );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String canUploadFiles( HttpServletRequest request, String strFieldName,
        List<FileItem> listFileItemsToUpload, Locale locale )
    {
        if ( StringUtils.isNotBlank( strFieldName ) && ( strFieldName.length(  ) > PREFIX_ENTRY_ID.length(  ) ) )
        {
            initMap( request.getSession(  ).getId(  ), strFieldName );

            String strTask = getEntryIdFromFieldName( strFieldName );

            if ( StringUtils.isEmpty( strTask ) || !StringUtils.isNumeric( strTask ) )
            {
                return I18nService.getLocalizedString( ERROR_MESSAGE_UNKNOWN_ERROR, locale );
            }

            TaskUploadConfig config = _taskUploadConfigService.findByPrimaryKey( Integer.valueOf( strTask ) );

            List<FileItem> list = getListUploadedFiles( strFieldName, request.getSession(  ) );

            if ( config.getMaxFile(  ) <= list.size(  ) )
            {
                Object[] params = new Object[1];
                params[0] = config.getMaxFile(  );

                return I18nService.getLocalizedString( ERROR_MESSAGE_TO_MANY_FILE, params, locale );
            }

            //  _taskUploadConfigService. 
            return null;
        }

        return I18nService.getLocalizedString( ERROR_MESSAGE_UNKNOWN_ERROR, locale );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FileItem> getListUploadedFiles( String strFieldName, HttpSession session )
    {
        if ( StringUtils.isBlank( strFieldName ) )
        {
            throw new AppException( "id field name is not provided for the current file upload" );
        }

        initMap( session.getId(  ), strFieldName );

        // find session-related files in the map
        Map<String, List<FileItem>> mapFileItemsSession = _mapAsynchronousUpload.get( session.getId(  ) );

        return mapFileItemsSession.get( strFieldName );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFileItemToUploadedFilesList( FileItem fileItem, String strFieldName, HttpServletRequest request )
    {
        // This is the name that will be displayed in the form. We keep
        // the original name, but clean it to make it cross-platform.
        String strFileName = UploadUtil.cleanFileName( fileItem.getName(  ).trim(  ) );

        initMap( request.getSession(  ).getId(  ), buildFieldName( strFieldName ) );

        // Check if this file has not already been uploaded
        List<FileItem> uploadedFiles = getListUploadedFiles( strFieldName, request.getSession(  ) );

        if ( uploadedFiles != null )
        {
            boolean bNew = true;

            if ( !uploadedFiles.isEmpty(  ) )
            {
                Iterator<FileItem> iterUploadedFiles = uploadedFiles.iterator(  );

                while ( bNew && iterUploadedFiles.hasNext(  ) )
                {
                    FileItem uploadedFile = iterUploadedFiles.next(  );
                    String strUploadedFileName = UploadUtil.cleanFileName( uploadedFile.getName(  ).trim(  ) );
                    // If we find a file with the same name and the same
                    // length, we consider that the current file has
                    // already been uploaded
                    bNew = !( StringUtils.equals( strUploadedFileName, strFileName ) &&
                        ( uploadedFile.getSize(  ) == fileItem.getSize(  ) ) );
                }
            }

            if ( bNew )
            {
                uploadedFiles.add( fileItem );
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeFileItem( String strFieldName, HttpSession session, int nIndex )
    {
        // Remove the file (this will also delete the file physically)
        List<FileItem> uploadedFiles = getListUploadedFiles( strFieldName, session );

        if ( ( uploadedFiles != null ) && !uploadedFiles.isEmpty(  ) && ( uploadedFiles.size(  ) > nIndex ) )
        {
            // Remove the object from the Hashmap
            FileItem fileItem = uploadedFiles.remove( nIndex );
            fileItem.delete(  );
        }
    }

    /**
     * Removes all files associated to the session
     * @param strSessionId the session id
     */
    public void removeSessionFiles( String strSessionId )
    {
        _mapAsynchronousUpload.remove( strSessionId );
    }

    /**
     * Build the field name from a given id entry
     * i.e. : form_1
     * @param strIdEntry the id entry
     * @return the field name
     */
    protected String buildFieldName( String strIdEntry )
    {
        return PREFIX_ENTRY_ID + strIdEntry;
    }

    /**
     * Get the id of the entry associated with a given field name
     * @param strFieldName The name of the field
     * @return The id of the entry
     */
    protected String getEntryIdFromFieldName( String strFieldName )
    {
        if ( StringUtils.isEmpty( strFieldName ) || ( strFieldName.length(  ) < PREFIX_ENTRY_ID.length(  ) ) )
        {
            return null;
        }

        return strFieldName.substring( PREFIX_ENTRY_ID.length(  ) );
    }

    /**
     * Init the map
     * @param strSessionId the session id
     * @param strFieldName the field name
     */
    private void initMap( String strSessionId, String strFieldName )
    {
        // find session-related files in the map
        Map<String, List<FileItem>> mapFileItemsSession = _mapAsynchronousUpload.get( strSessionId );

        // create map if not exists
        if ( mapFileItemsSession == null )
        {
            synchronized ( this )
            {
                // Ignore double check locking error : assignation and instanciation of objects are separated.
                mapFileItemsSession = _mapAsynchronousUpload.get( strSessionId );

                if ( mapFileItemsSession == null )
                {
                    mapFileItemsSession = new ConcurrentHashMap<String, List<FileItem>>(  );
                    _mapAsynchronousUpload.put( strSessionId, mapFileItemsSession );
                }
            }
        }

        List<FileItem> listFileItems = mapFileItemsSession.get( strFieldName );

        if ( listFileItems == null )
        {
            listFileItems = new ArrayList<FileItem>(  );
            mapFileItemsSession.put( strFieldName, listFileItems );
        }
    }

    @Override
    public String getHandlerName(  )
    {
        return HANDLER_NAME;
    }
}
