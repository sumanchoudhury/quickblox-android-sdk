package com.quickblox.chat_v2.apis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.quickblox.chat_v2.core.ChatApplication;
import com.quickblox.chat_v2.interfaces.OnFileUploadComplete;
import com.quickblox.chat_v2.interfaces.OnPictureDownloadComplete;
import com.quickblox.chat_v2.interfaces.OnUserProfileDownloaded;
import com.quickblox.chat_v2.utils.GlobalConsts;
import com.quickblox.core.QBCallback;
import com.quickblox.core.QBCallbackImpl;
import com.quickblox.core.QBRequestCanceler;
import com.quickblox.core.result.Result;
import com.quickblox.module.content.QBContent;
import com.quickblox.module.content.result.QBFileDownloadResult;
import com.quickblox.module.content.result.QBFileUploadTaskResult;
import com.quickblox.module.users.QBUsers;
import com.quickblox.module.users.model.QBUser;
import com.quickblox.module.users.result.QBUserPagedResult;
import com.quickblox.module.users.result.QBUserResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class QuickBloxManager {

    private ChatApplication app;

    private OnUserProfileDownloaded userProfileListener;
    private OnPictureDownloadComplete pictureDownloadComplete;
    private OnFileUploadComplete uploadListener;

    private boolean pictureMode;
    private int currentFileId;

    private Context context;
    private QBUser qbuser;

    public QuickBloxManager(Context context) {
        app = ChatApplication.getInstance();
        this.context = context;

    }

    public synchronized void getQbUsersFromCollection(List<String> usersIds, byte contexts) {

        QBUsers.getUsersByIDs(usersIds, new QBCallbackImpl() {

            @Override
            public void onComplete(Result result, Object context) {

                QBUserPagedResult usersResult = (QBUserPagedResult) result;

                switch ((Byte) context) {
                    case (GlobalConsts.DOWNLOAD_LIST_FOR_DIALOG): {

                        for (QBUser qu : usersResult.getUsers()) {
                            app.getDialogsUsersMap().put(String.valueOf(qu.getId()), qu);
                        }
                    }
                    break;
                    case (GlobalConsts.DOWNLOAD_LIST_FOR_CONTACTS_CANDIDATE):
                        app.setContactsCandidateList(usersResult.getUsers());
                        break;

                    case (GlobalConsts.DOWNLOAD_LIST_FOR_CONTACTS): {
                        app.setContactsList(usersResult.getUsers());
                        for (QBUser contact : usersResult.getUsers()) {
                            app.getContactsMap().put(String.valueOf(contact.getId()), contact);
                        }

                        break;
                    }
                }


                if (userProfileListener != null) {
                    userProfileListener.downloadComlete(null);
                }
            }
        }, contexts);

    }

    // WARNING ! upload section
    public void uploadPic(File file, boolean isFileTransferAttach) {
        pictureMode = isFileTransferAttach;

        QBRequestCanceler requestCanceler = QBContent.uploadFileTask(file, true, new QBCallbackImpl() {
            @Override
            public void onComplete(Result result) {

                if (result.isSuccess()) {
                    QBFileUploadTaskResult fileUploadTaskResultResult = (QBFileUploadTaskResult) result;
                    if (!pictureMode) {
                        app.getQbUser().setFileId(fileUploadTaskResultResult.getFile().getId());
                        updateQBUser(app.getQbUser());

                    } else {

                        uploadListener.uploadComplete(fileUploadTaskResultResult.getFile().getId(), fileUploadTaskResultResult.getFile().getPublicUrl());
                    }
                }
            }
        });
    }

    private void updateQBUser(QBUser upadtedUser) {
        qbuser = upadtedUser;
        qbuser.setPassword(null);

        QBUsers.updateUser(upadtedUser, new QBCallbackImpl() {

            @Override
            public void onComplete(Result result) {
                uploadListener.uploadComplete(qbuser.getFileId(), null);
            }

        });
    }

    public void downloadQBFile(QBUser currentUser) {
        if (currentUser.getFileId() == null) {
            return;
        }
        File targetFile = new File(context.getCacheDir(), String.valueOf(currentUser.getFileId()) + ".jpg");

        if (targetFile.exists()) {
            Bitmap userPic = BitmapFactory.decodeFile(String.valueOf(currentUser.getFileId()) + ".jpg");
            pictureDownloadComplete.downloadComlete(userPic, targetFile);
            return;
        }

        currentFileId = currentUser.getFileId();
        QBContent.downloadFileTask(currentUser.getFileId(), new QBCallbackImpl() {

            @Override
            public void onComplete(Result result) {

                QBFileDownloadResult qbFileDownloadResult = (QBFileDownloadResult) result;
                if (result.isSuccess()) {

                    InputStream is = qbFileDownloadResult.getContentStream();
                    Bitmap userPic = BitmapFactory.decodeStream(is);

                    File userPicFile = new File(context.getCacheDir(), String.valueOf(currentFileId) + ".jpg");
                    FileOutputStream fos;
                    try {
                        fos = new FileOutputStream(userPicFile);
                        writeFromInputToOutput(is, fos);
                        is.close();
                        fos.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    pictureDownloadComplete.downloadComlete(userPic, userPicFile);
                }
            }
        });
    }

    public void getSingleUserInfo(int userId) {
        QBUsers.getUser(userId, new QBCallback() {

            @Override
            public void onComplete(Result result, Object context) {
            }

            @Override
            public void onComplete(Result result) {
                userProfileListener.downloadComlete(((QBUserResult) result).getUser());
            }
        });
    }


    private void writeFromInputToOutput(InputStream source, OutputStream dest) {
        final int BUFFER_SIZE = 2048;
        final int EOF_MARK = -1;

        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead = EOF_MARK;

        try {
            while ((bytesRead = source.read(buffer)) != EOF_MARK) {
                dest.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // LISTENERS

    public void setUserProfileListener(OnUserProfileDownloaded friendProvileListener) {
        this.userProfileListener = friendProvileListener;
    }

    public void setPictureDownloadComplete(OnPictureDownloadComplete pictureDownloadComplete) {
        this.pictureDownloadComplete = pictureDownloadComplete;
    }

    public void setUploadListener(OnFileUploadComplete uploadListener) {
        this.uploadListener = uploadListener;
    }
}
