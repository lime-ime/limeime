package org.limeime.ui.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import org.limeime.data.ImConfig;

public class ImNavigationViewModel extends ViewModel {
    // null = no selection / show placeholder
    public final MutableLiveData<ImConfig> selectedIm = new MutableLiveData<>(null);
    // true = show ImInstallFragment in detail pane
    public final MutableLiveData<Boolean> showInstall = new MutableLiveData<>(false);
}
