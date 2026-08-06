import {
    LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts';

const SalesChart = ({ data }) => {
    if (!data || data.length === 0) {
        return (
            <div className="flex items-center justify-center h-64 text-gray-400">
                No sales data available
            </div>
        );
    }

    const chartData = data.map(item => ({
        month: item.label,
        revenue: Math.round(item.value * 100) / 100,
    }));

    return (
        <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e0e0e0" />
                <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip
                    formatter={(value) => [`$${value}`, 'Revenue']}
                    contentStyle={{ borderRadius: '8px', border: '1px solid #e0e0e0' }}
                />
                <Legend />
                <Line
                    type="monotone"
                    dataKey="revenue"
                    stroke="#1976d2"
                    strokeWidth={3}
                    dot={{ r: 5 }}
                    activeDot={{ r: 8 }}
                    name="Revenue ($)"
                />
            </LineChart>
        </ResponsiveContainer>
    );
};

export default SalesChart;
