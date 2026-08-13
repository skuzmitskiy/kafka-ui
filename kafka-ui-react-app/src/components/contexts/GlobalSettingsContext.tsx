import React from 'react';
import { useTimeFormat } from 'lib/hooks/api/timeFormat';
import { useAppInfo } from 'lib/hooks/api/appConfig';
import { ApplicationInfoEnabledFeaturesEnum } from 'generated-sources';

interface GlobalSettingsContextValue {
  timeStampFormat: string;
  hasDynamicConfig: boolean;
}

export const defaultGlobalSettingsValue: GlobalSettingsContextValue = {
  timeStampFormat: 'DD.MM.YYYY HH:mm:ss',
  hasDynamicConfig: false,
};

export const GlobalSettingsContext =
  React.createContext<GlobalSettingsContextValue>(defaultGlobalSettingsValue);

export const GlobalSettingsProvider: React.FC<
  React.PropsWithChildren<unknown>
> = ({ children }) => {
  const { data } = useTimeFormat();
  const { data: appInfo } = useAppInfo();

  const hasDynamicConfig = !!appInfo?.enabledFeatures?.includes(
    ApplicationInfoEnabledFeaturesEnum.DYNAMIC_CONFIG
  );

  return (
    <GlobalSettingsContext.Provider
      value={{
        timeStampFormat:
          data?.timeStampFormat || defaultGlobalSettingsValue.timeStampFormat,
        hasDynamicConfig,
      }}
    >
      {children}
    </GlobalSettingsContext.Provider>
  );
};
