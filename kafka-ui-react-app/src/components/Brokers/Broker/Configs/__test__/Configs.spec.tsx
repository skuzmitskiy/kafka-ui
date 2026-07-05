import React from 'react';
import { screen } from '@testing-library/dom';
import { cleanup } from '@testing-library/react';
import { render, WithRoute } from 'lib/testHelpers';
import { clusterBrokerConfigsPath } from 'lib/paths';
import { useBrokerConfig } from 'lib/hooks/api/brokers';
import { brokerConfigPayload } from 'lib/fixtures/brokers';
import Configs from 'components/Brokers/Broker/Configs/Configs';
import userEvent from '@testing-library/user-event';

const clusterName = 'Cluster_Name';
const brokerId = 'Broker_Id';

jest.mock('lib/hooks/api/brokers', () => ({
  useBrokerConfig: jest.fn(),
  useUpdateBrokerConfigByName: jest.fn(),
}));

jest.mock('use-debounce', () => ({
  useDebouncedCallback: (fn: (e: Event) => void) => fn,
}));

describe('Configs', () => {
  const renderComponent = () => {
    const path = clusterBrokerConfigsPath(clusterName, brokerId);
    return render(
      <WithRoute path={clusterBrokerConfigsPath()}>
        <Configs />
      </WithRoute>,
      { initialEntries: [path] }
    );
  };

  beforeEach(() => {
    (useBrokerConfig as jest.Mock).mockImplementation(() => ({
      data: brokerConfigPayload,
    }));
    renderComponent();
  });

  it('renders configs table', async () => {
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getAllByRole('row').length).toEqual(
      brokerConfigPayload.length + 1
    );
  });

  it('filters by value including null values', async () => {
    const configsWithNull = [
      ...brokerConfigPayload,
      {
        name: 'some.config.with.null.value',
        value: null,
        source: 'DEFAULT_CONFIG',
        isSensitive: false,
        isReadOnly: false,
        synonyms: [],
      },
    ];
    (useBrokerConfig as jest.Mock).mockImplementation(() => ({
      data: configsWithNull,
    }));
    cleanup();
    renderComponent();

    const searchInput = screen.getByPlaceholderText('Search by Key or Value');
    await userEvent.type(searchInput, 'null');

    expect(screen.getByText('some.config.with.null.value')).toBeInTheDocument();
    expect(screen.queryByText('compression.type')).not.toBeInTheDocument();
  });

  it('updates textbox value', async () => {
    await userEvent.click(screen.getAllByLabelText('editAction')[0]);

    const textbox = screen.getByLabelText('inputValue');
    expect(textbox).toBeInTheDocument();
    expect(textbox).toHaveValue('producer');

    await userEvent.type(textbox, 'new value');

    expect(
      screen.getByRole('button', { name: 'confirmAction' })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'cancelAction' })
    ).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole('button', { name: 'confirmAction' })
    );

    expect(
      screen.getByText('Are you sure you want to change the value?')
    ).toBeInTheDocument();
  });
});
