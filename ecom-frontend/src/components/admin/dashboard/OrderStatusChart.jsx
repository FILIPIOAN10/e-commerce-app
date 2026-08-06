import {
    PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend
} from 'recharts';

const STATUS_COLORS = {
    'Placed': '#2196f3',
    'Packed': '#ff9800',
    'Shipped': '#9c27b0',
    'Delivered': '#4caf50',
    'Cancelled': '#f44336',
};

const OrderStatusChart = ({ data }) => {
    if (!data || data.length === 0) {
        return (
            <div className="flex items-center justify-center h-64 text-gray-400">
                No order status data available
            </div>
        );
    }

    const chartData = data.map(item => ({
        name: item.label,
        value: item.value,
    }));

    return (
        <ResponsiveContainer width="100%" height={300}>
            <PieChart>
                <Pie
                    data={chartData}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ name, value }) => `${name}: ${value}`}
                    outerRadius={90}
                    fill="#8884d8"
                    dataKey="value"
                >
                    {chartData.map((entry, index) => (
                        <Cell
                            key={`cell-${index}`}
                            fill={STATUS_COLORS[entry.name] || '#8884d8'}
                        />
                    ))}
                </Pie>
                <Tooltip
                    contentStyle={{ borderRadius: '8px', border: '1px solid #e0e0e0' }}
                />
                <Legend />
            </PieChart>
        </ResponsiveContainer>
    );
};

export default OrderStatusChart;
